package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.client.StructuredJsonResult;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.parser.PlainTextParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final Long MEMBER_ID = 1L;

    private LlmClient openAiClient;
    private TreeValidator treeValidator;
    private GeneratedTreeWriter writer;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        openAiClient = mock(LlmClient.class);
        treeValidator = mock(TreeValidator.class);
        writer = mock(GeneratedTreeWriter.class);
        ObjectMapper objectMapper = JsonMapper.builder().build();

        lenient().when(openAiClient.getProvider()).thenReturn(LlmProvider.OPENAI);

        chatService = new ChatService(
            List.of(openAiClient), objectMapper, treeValidator, writer,
            new DocumentTextExtractor(List.of(new PlainTextParser())));
    }

    private MockMultipartFile file(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private ChatReqDTO req(String message, LlmProvider provider) {
        return new ChatReqDTO(message, provider);
    }

    @Test
    @DisplayName("메시지가 비어 있으면 EMPTY_MESSAGE")
    void emptyMessage() {
        assertError(() -> chatService.generateTree(MEMBER_ID, req("  ", LlmProvider.OPENAI)),
            ChatErrorCode.EMPTY_MESSAGE);
    }

    @Test
    @DisplayName("지원하지 않는 provider 면 UNSUPPORTED_PROVIDER")
    void unsupportedProvider() {
        assertError(() -> chatService.generateTree(MEMBER_ID, req("트리 만들어줘", LlmProvider.OLLAMA)),
            ChatErrorCode.UNSUPPORTED_PROVIDER);
    }

    @Test
    @DisplayName("provider 를 생략하면 기본 provider(OPENAI)로 호출한다")
    void nullProviderFallsBackToDefault() {
        stubTreeJson();

        TreeGenResDTO result = chatService.generateTree(MEMBER_ID, null, "트리 만들어줘", file("note.md", "본문"));

        assertThat(result.treeId()).isEqualTo(42L);
        verify(openAiClient).generateTreeJson(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("LLM 응답이 JSON 이 아니면 LLM_RESPONSE_INVALID")
    void invalidJson() {
        when(openAiClient.generateTreeJson("트리 만들어줘")).thenReturn(result("이건 JSON 이 아님"));

        assertError(() -> chatService.generateTree(MEMBER_ID, req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("검증 실패 예외는 그대로 전파된다")
    void validationFailurePropagates() {
        when(openAiClient.generateTreeJson("트리 만들어줘"))
            .thenReturn(result("{\"root\":null}"));
        doThrow(new ChatException(ChatErrorCode.LLM_RESPONSE_INVALID))
            .when(treeValidator).validate(org.mockito.ArgumentMatchers.any());

        assertError(() -> chatService.generateTree(MEMBER_ID, req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("정상 응답이면 파싱→검증→저장 후 결과를 반환한다")
    void happyPath() {
        String json = """
            {"root":{"name":"루트","memo":null,"children":[]}}
            """;
        when(openAiClient.generateTreeJson("자료구조 트리 만들어줘")).thenReturn(result(json));

        TreeGenResDTO expected = TreeGenResDTO.builder().treeId(42L).nodes(List.of()).build();
        when(writer.save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        TreeGenResDTO result = chatService.generateTree(MEMBER_ID, req("자료구조 트리 만들어줘", LlmProvider.OPENAI));

        assertThat(result.treeId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("메시지와 파일이 모두 없으면 INPUT_REQUIRED")
    void fileAndMessageBothMissing() {
        assertError(() -> chatService.generateTree(MEMBER_ID, LlmProvider.OPENAI, "  ", null),
            ChatErrorCode.INPUT_REQUIRED);
    }

    @Test
    @DisplayName("파일만 있어도 트리를 생성한다")
    void fileOnly() {
        stubTreeJson();

        TreeGenResDTO result = chatService.generateTree(
            MEMBER_ID, LlmProvider.OPENAI, null, file("note.md", "이분탐색은 정렬된 배열에서 값을 찾는 알고리즘이다."));

        assertThat(result.treeId()).isEqualTo(42L);
        // 파일 본문과 기본 지시문이 함께 프롬프트에 실려야 한다
        assertThat(capturedPrompt()).contains("이분탐색은 정렬된 배열에서").contains("<document>");
    }

    @Test
    @DisplayName("파일과 지시문을 함께 주면 둘 다 프롬프트에 포함된다")
    void fileWithInstruction() {
        stubTreeJson();

        chatService.generateTree(MEMBER_ID, LlmProvider.OPENAI, "3단계로 정리해줘", file("note.txt", "트리 자료구조 정리"));

        assertThat(capturedPrompt())
            .contains("트리 자료구조 정리")
            .contains("3단계로 정리해줘");
    }

    @Test
    @DisplayName("지원하지 않는 확장자면 UNSUPPORTED_FILE_TYPE")
    void unsupportedExtension() {
        assertError(() -> chatService.generateTree(MEMBER_ID, LlmProvider.OPENAI, null, file("report.pdf", "내용")),
            ChatErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("파일 검증 실패 시 LLM 을 호출하지 않는다")
    void invalidFileDoesNotCallLlm() {
        assertError(() -> chatService.generateTree(MEMBER_ID, LlmProvider.OPENAI, null, file("a.exe", "내용")),
            ChatErrorCode.UNSUPPORTED_FILE_TYPE);

        verify(openAiClient, never()).generateTreeJson(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("JSON 경로 메시지가 상한을 넘으면 LLM 호출 없이 MESSAGE_TOO_LONG")
    void jsonMessageTooLong() {
        String tooLong = "가".repeat(ChatReqDTO.MESSAGE_MAX_LENGTH + 1);

        assertError(() -> chatService.generateTree(MEMBER_ID, req(tooLong, LlmProvider.OPENAI)),
            ChatErrorCode.MESSAGE_TOO_LONG);
        verify(openAiClient, never()).generateTreeJson(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("파일 경로 지시문이 상한을 넘으면 파일 추출·LLM 호출 없이 MESSAGE_TOO_LONG")
    void fileMessageTooLong() {
        String tooLong = "가".repeat(ChatReqDTO.MESSAGE_MAX_LENGTH + 1);

        // 확장자가 잘못된 파일을 넣어, 파일 추출보다 길이 검사가 먼저인지도 함께 확인한다
        assertError(() -> chatService.generateTree(MEMBER_ID, LlmProvider.OPENAI, tooLong, file("a.exe", "내용")),
            ChatErrorCode.MESSAGE_TOO_LONG);
        verify(openAiClient, never()).generateTreeJson(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("지시문이 상한과 같으면 통과한다")
    void messageAtLimit() {
        stubTreeJson();
        String atLimit = "가".repeat(ChatReqDTO.MESSAGE_MAX_LENGTH);

        chatService.generateTree(MEMBER_ID, LlmProvider.OPENAI, atLimit, null);

        assertThat(capturedPrompt()).contains(atLimit);
    }

    @Test
    @DisplayName("토큰 상한에 걸려 잘린 응답이면 LLM_RESPONSE_INVALID")
    void truncatedResponse() {
        when(openAiClient.generateTreeJson("트리 만들어줘")).thenReturn(Mono.just(
            new StructuredJsonResult("{\"root\":{\"name\":\"루", "gpt-test", "length", null, 10, 16000)));

        assertError(() -> chatService.generateTree(MEMBER_ID, req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
        verify(writer, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("모델이 거절해 본문이 없으면 LLM_RESPONSE_INVALID")
    void refusedResponse() {
        when(openAiClient.generateTreeJson("트리 만들어줘")).thenReturn(Mono.just(
            new StructuredJsonResult(null, "gpt-test", "stop", "거절합니다", 10, 3)));

        assertError(() -> chatService.generateTree(MEMBER_ID, req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("유효하지 않은 응답을 받으면 한 번 다시 생성해 저장한다")
    void retriesInvalidResponseOnce() {
        String valid = """
            {"root":{"name":"루트","memo":null,"children":[]}}
            """;
        when(openAiClient.generateTreeJson("트리 만들어줘")).thenReturn(result("JSON 아님"), result(valid));
        when(writer.save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(TreeGenResDTO.builder().treeId(42L).nodes(List.of()).build());

        TreeGenResDTO saved = chatService.generateTree(MEMBER_ID, req("트리 만들어줘", LlmProvider.OPENAI));

        assertThat(saved.treeId()).isEqualTo(42L);
        verify(openAiClient, times(2)).generateTreeJson("트리 만들어줘");
    }

    @Test
    @DisplayName("재생성도 실패하면 호출을 멈추고 LLM_RESPONSE_INVALID")
    void stopsAfterMaxAttempts() {
        when(openAiClient.generateTreeJson("트리 만들어줘")).thenReturn(result("JSON 아님"));

        assertError(() -> chatService.generateTree(MEMBER_ID, req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
        verify(openAiClient, times(ChatService.MAX_GENERATION_ATTEMPTS)).generateTreeJson("트리 만들어줘");
    }

    @Test
    @DisplayName("모델의 거절과 호출 실패는 재생성하지 않는다")
    void doesNotRetryRefusalOrCallFailure() {
        when(openAiClient.generateTreeJson("거절")).thenReturn(Mono.just(
            new StructuredJsonResult(null, "gpt-test", "stop", "거절합니다", 10, 3)));
        when(openAiClient.generateTreeJson("실패")).thenReturn(Mono.error(new IllegalStateException("500")));

        assertError(() -> chatService.generateTree(MEMBER_ID, req("거절", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
        assertError(() -> chatService.generateTree(MEMBER_ID, req("실패", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_CALL_FAILED);

        verify(openAiClient, times(1)).generateTreeJson("거절");
        verify(openAiClient, times(1)).generateTreeJson("실패");
    }

    @Test
    @DisplayName("재생성의 대기 상한은 전체 예산에서 이미 쓴 시간을 뺀 값이다")
    void retryTimeoutUsesRemainingBudget() {
        assertThat(ChatService.attemptTimeout(Duration.ZERO)).isEqualTo(Duration.ofSeconds(65));
        assertThat(ChatService.attemptTimeout(Duration.ofSeconds(20))).isEqualTo(Duration.ofSeconds(65));
        assertThat(ChatService.attemptTimeout(Duration.ofSeconds(50))).isEqualTo(Duration.ofSeconds(40));
    }

    // --- 기존 워크스페이스에 채우는 경로 ---

    @Test
    @DisplayName("워크스페이스를 지정하면 saveToWorkspace 로 저장한다")
    void workspacePathSavesToWorkspace() {
        String json = """
            {"root":{"name":"루트","memo":null,"children":[]}}
            """;
        when(openAiClient.generateTreeJson(org.mockito.ArgumentMatchers.anyString())).thenReturn(result(json));
        when(writer.saveToWorkspace(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L),
            org.mockito.ArgumentMatchers.any()))
            .thenReturn(TreeGenResDTO.builder().treeId(9L).workspaceId(7L).nodes(List.of()).build());

        TreeGenResDTO result = chatService.generateTreeInWorkspace(
            MEMBER_ID, 7L, req("트리 만들어줘", LlmProvider.OPENAI));

        assertThat(result.workspaceId()).isEqualTo(7L);
        verify(writer).saveToWorkspace(org.mockito.ArgumentMatchers.eq(MEMBER_ID),
            org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any());
        // 워크스페이스를 새로 만드는 저장 경로는 쓰지 않아야 한다
        verify(writer, never()).save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("워크스페이스 검증이 실패하면 LLM 을 호출하지 않는다")
    void workspaceCheckBeforeLlmCall() {
        doThrow(new ChatException(ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY))
            .when(writer).validateWritableWorkspace(MEMBER_ID, 7L);

        assertError(() -> chatService.generateTreeInWorkspace(MEMBER_ID, 7L, req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);

        verify(openAiClient, never()).generateTreeJson(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("입력 검증이 워크스페이스 검증보다 먼저다")
    void inputCheckBeforeWorkspaceCheck() {
        assertError(() -> chatService.generateTreeInWorkspace(MEMBER_ID, 7L, req("  ", LlmProvider.OPENAI)),
            ChatErrorCode.EMPTY_MESSAGE);

        verify(writer, never()).validateWritableWorkspace(org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("워크스페이스 검증이 문서 파싱보다 먼저다")
    void workspaceCheckBeforeDocumentParsing() {
        doThrow(new ChatException(ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY))
            .when(writer).validateWritableWorkspace(MEMBER_ID, 7L);

        // 확장자가 잘못된 파일을 넣어도 워크스페이스 검증 결과가 먼저 나와야 한다
        assertError(() -> chatService.generateTreeInWorkspace(
                MEMBER_ID, 7L, LlmProvider.OPENAI, null, file("a.exe", "내용")),
            ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);

        verify(openAiClient, never()).generateTreeJson(org.mockito.ArgumentMatchers.anyString());
    }

    private static Mono<StructuredJsonResult> result(String content) {
        return Mono.just(new StructuredJsonResult(content, "gpt-test", "stop", null, 10, 20));
    }

    private void stubTreeJson() {
        String json = """
            {"root":{"name":"루트","memo":null,"children":[]}}
            """;
        when(openAiClient.generateTreeJson(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(result(json));
        when(writer.save(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(TreeGenResDTO.builder().treeId(42L).nodes(List.of()).build());
    }

    private String capturedPrompt() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(openAiClient).generateTreeJson(captor.capture());
        return captor.getValue();
    }

    private void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

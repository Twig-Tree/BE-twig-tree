package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.LlmProvider;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

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
        assertError(() -> chatService.generateTree(req("  ", LlmProvider.OPENAI)),
            ChatErrorCode.EMPTY_MESSAGE);
    }

    @Test
    @DisplayName("지원하지 않는 provider 면 UNSUPPORTED_PROVIDER")
    void unsupportedProvider() {
        assertError(() -> chatService.generateTree(req("트리 만들어줘", LlmProvider.OLLAMA)),
            ChatErrorCode.UNSUPPORTED_PROVIDER);
    }

    @Test
    @DisplayName("LLM 응답이 JSON 이 아니면 LLM_RESPONSE_INVALID")
    void invalidJson() {
        when(openAiClient.generateTreeJson("트리 만들어줘")).thenReturn(Mono.just("이건 JSON 이 아님"));

        assertError(() -> chatService.generateTree(req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("검증 실패 예외는 그대로 전파된다")
    void validationFailurePropagates() {
        when(openAiClient.generateTreeJson("트리 만들어줘"))
            .thenReturn(Mono.just("{\"nodes\":[]}"));
        doThrow(new ChatException(ChatErrorCode.LLM_RESPONSE_INVALID))
            .when(treeValidator).validate(org.mockito.ArgumentMatchers.any());

        assertError(() -> chatService.generateTree(req("트리 만들어줘", LlmProvider.OPENAI)),
            ChatErrorCode.LLM_RESPONSE_INVALID);
    }

    @Test
    @DisplayName("정상 응답이면 파싱→검증→저장 후 결과를 반환한다")
    void happyPath() {
        String json = """
            {"nodes":[
              {"tempId":1,"name":"루트","memo":null,"parentTempId":null,"orderId":1}
            ]}
            """;
        when(openAiClient.generateTreeJson("자료구조 트리 만들어줘")).thenReturn(Mono.just(json));

        TreeGenResDTO expected = TreeGenResDTO.builder().treeId(42L).nodes(List.of()).build();
        when(writer.save(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        TreeGenResDTO result = chatService.generateTree(req("자료구조 트리 만들어줘", LlmProvider.OPENAI));

        assertThat(result.treeId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("메시지와 파일이 모두 없으면 INPUT_REQUIRED")
    void fileAndMessageBothMissing() {
        assertError(() -> chatService.generateTree(LlmProvider.OPENAI, "  ", null),
            ChatErrorCode.INPUT_REQUIRED);
    }

    @Test
    @DisplayName("파일만 있어도 트리를 생성한다")
    void fileOnly() {
        stubTreeJson();

        TreeGenResDTO result = chatService.generateTree(
            LlmProvider.OPENAI, null, file("note.md", "이분탐색은 정렬된 배열에서 값을 찾는 알고리즘이다."));

        assertThat(result.treeId()).isEqualTo(42L);
        // 파일 본문과 기본 지시문이 함께 프롬프트에 실려야 한다
        assertThat(capturedPrompt()).contains("이분탐색은 정렬된 배열에서").contains("<document>");
    }

    @Test
    @DisplayName("파일과 지시문을 함께 주면 둘 다 프롬프트에 포함된다")
    void fileWithInstruction() {
        stubTreeJson();

        chatService.generateTree(LlmProvider.OPENAI, "3단계로 정리해줘", file("note.txt", "트리 자료구조 정리"));

        assertThat(capturedPrompt())
            .contains("트리 자료구조 정리")
            .contains("3단계로 정리해줘");
    }

    @Test
    @DisplayName("지원하지 않는 확장자면 UNSUPPORTED_FILE_TYPE")
    void unsupportedExtension() {
        assertError(() -> chatService.generateTree(LlmProvider.OPENAI, null, file("report.pdf", "내용")),
            ChatErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("파일 검증 실패 시 LLM 을 호출하지 않는다")
    void invalidFileDoesNotCallLlm() {
        assertError(() -> chatService.generateTree(LlmProvider.OPENAI, null, file("a.exe", "내용")),
            ChatErrorCode.UNSUPPORTED_FILE_TYPE);

        verify(openAiClient, never()).generateTreeJson(org.mockito.ArgumentMatchers.anyString());
    }

    private void stubTreeJson() {
        String json = """
            {"nodes":[{"tempId":1,"name":"루트","memo":null,"parentTempId":null,"orderId":1}]}
            """;
        when(openAiClient.generateTreeJson(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(Mono.just(json));
        when(writer.save(org.mockito.ArgumentMatchers.any()))
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

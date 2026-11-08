package com.tree.twig_tree.domain.lecture.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.client.StructuredJsonRequest;
import com.tree.twig_tree.domain.chat.client.StructuredJsonResult;
import com.tree.twig_tree.domain.lecture.dto.LectureGenResDTO;
import com.tree.twig_tree.domain.lecture.dto.LectureReqDTO;
import com.tree.twig_tree.domain.lecture.exception.LectureException;
import com.tree.twig_tree.domain.lecture.exception.code.LectureErrorCode;
import com.tree.twig_tree.domain.lecture.prompt.LecturePrompt;
import com.tree.twig_tree.domain.tree.exception.TreeException;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LectureGenerationServiceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long TREE_ID = 10L;
    private static final String MODEL = "gpt-4o-mini-test";
    private static final int MAX_TOKENS = 8000;
    private static final String OUTLINE = "- [1] 척추동물\n  - [2] 포유류\n  - [3] 파충류";
    private static final String REQUEST = "포유류와 파충류가 어떻게 다른지 비교해서 설명해 줘.";
    private static final String RESPONSE_TEXT = "{\"scenarioSchemaVersion\":1,\"scenes\":[]}";

    private LlmClient openAiClient;
    private TreeRepository treeRepository;
    private LectureScenarioResources resources;
    private LectureGenerationService service;

    @BeforeEach
    void setUp() {
        openAiClient = mock(LlmClient.class);
        treeRepository = mock(TreeRepository.class);
        resources = new LectureScenarioResources(JsonMapper.builder().build());

        lenient().when(openAiClient.getProvider()).thenReturn(LlmProvider.OPENAI);
        lenient().when(openAiClient.supportsStrictJsonSchema()).thenReturn(true);
        lenient().when(treeRepository.findOwnerMemberIdById(TREE_ID)).thenReturn(Optional.of(MEMBER_ID));

        service = new LectureGenerationService(
            List.of(openAiClient), treeRepository, resources, MODEL, MAX_TOKENS);
    }

    private LectureReqDTO req() {
        return req(resources.scenarioSchemaVersion(), null, null);
    }

    private LectureReqDTO req(Integer schemaVersion, LectureReqDTO.Repair repair, LlmProvider provider) {
        return new LectureReqDTO(TREE_ID, REQUEST, OUTLINE, schemaVersion, repair, provider);
    }

    private StructuredJsonResult result(String content, String finishReason, String refusal) {
        return new StructuredJsonResult(content, "gpt-4o-mini-2024-07-18", finishReason, refusal, 1200, 340);
    }

    private StructuredJsonResult success() {
        return result(RESPONSE_TEXT, "stop", null);
    }

    @Test
    @DisplayName("모델 응답 본문을 손대지 않고 그대로 반환한다")
    void returnsResponseTextAsIs() {
        when(openAiClient.generateStructuredJson(any())).thenReturn(Mono.just(success()));

        LectureGenResDTO res = service.generate(MEMBER_ID, req());

        assertThat(res.responseText()).isEqualTo(RESPONSE_TEXT);
        assertThat(res.model()).isEqualTo("gpt-4o-mini-2024-07-18");
        assertThat(res.promptVersion()).isEqualTo(LecturePrompt.PROMPT_VERSION);
        assertThat(res.usage().inputTokens()).isEqualTo(1200);
        assertThat(res.usage().outputTokens()).isEqualTo(340);
        assertThat(res.attemptCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("첫 요청은 system 과 user 메시지만 보내고, 설정한 모델과 토큰 상한을 싣는다")
    void buildsFirstRequest() {
        when(openAiClient.generateStructuredJson(any())).thenReturn(Mono.just(success()));

        service.generate(MEMBER_ID, req());

        StructuredJsonRequest sent = captureRequest();
        assertThat(sent.model()).isEqualTo(MODEL);
        assertThat(sent.maxOutputTokens()).isEqualTo(MAX_TOKENS);
        assertThat(sent.schemaName()).isEqualTo(LecturePrompt.SCHEMA_NAME);
        assertThat(sent.schema()).isEqualTo(resources.strictSchema());

        assertThat(sent.messages()).hasSize(2);
        assertThat(sent.messages().get(0).role()).isEqualTo("system");
        assertThat(sent.messages().get(0).content()).contains(resources.vocabularyJson());
        assertThat(sent.messages().get(1).role()).isEqualTo("user");
        assertThat(sent.messages().get(1).content()).contains(OUTLINE).contains(REQUEST);
    }

    @Test
    @DisplayName("repair 요청은 직전 응답을 assistant 로, 문제를 user 로 덧붙인다")
    void buildsRepairRequest() {
        when(openAiClient.generateStructuredJson(any())).thenReturn(Mono.just(success()));

        LectureReqDTO.Repair repair = new LectureReqDTO.Repair(1, "{\"scenes\":[]}", List.of(
            new LectureReqDTO.Repair.Issue("node", "$.scenes[0]", "트리에 없는 노드(999)입니다.")
        ), 0);

        service.generate(MEMBER_ID, req(resources.scenarioSchemaVersion(), repair, null));

        StructuredJsonRequest sent = captureRequest();
        assertThat(sent.messages()).hasSize(4);
        assertThat(sent.messages().get(2).role()).isEqualTo("assistant");
        assertThat(sent.messages().get(2).content()).isEqualTo("{\"scenes\":[]}");
        assertThat(sent.messages().get(3).role()).isEqualTo("user");
        assertThat(sent.messages().get(3).content())
            .contains("$.scenes[0]")
            .contains("트리에 없는 노드(999)입니다.");
        // repair 도 첫 요청과 같은 outline·어휘를 다시 싣는다.
        assertThat(sent.messages().get(1).content()).contains(OUTLINE);
    }

    @Test
    @DisplayName("없는 트리면 모델을 부르지 않는다")
    void treeNotFound() {
        when(treeRepository.findOwnerMemberIdById(TREE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req()))
            .isInstanceOf(TreeException.class)
            .hasFieldOrPropertyWithValue("errorCode", TreeErrorCode.TREE_NOT_FOUND);

        verify(openAiClient, never()).generateStructuredJson(any());
    }

    @Test
    @DisplayName("남의 트리면 모델을 부르지 않는다")
    void treeAccessDenied() {
        when(treeRepository.findOwnerMemberIdById(TREE_ID)).thenReturn(Optional.of(99L));

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req()))
            .isInstanceOf(TreeException.class)
            .hasFieldOrPropertyWithValue("errorCode", TreeErrorCode.TREE_ACCESS_DENIED);

        verify(openAiClient, never()).generateStructuredJson(any());
    }

    @Test
    @DisplayName("스키마 버전이 다르면 모델을 부르지 않는다")
    void schemaVersionMismatch() {
        int otherVersion = resources.scenarioSchemaVersion() + 1;

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req(otherVersion, null, null)))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.SCHEMA_VERSION_MISMATCH);

        verify(openAiClient, never()).generateStructuredJson(any());
    }

    @Test
    @DisplayName("repair 횟수를 넘기면 모델을 부르지 않는다")
    void repairAttemptExceeded() {
        LectureReqDTO.Repair repair = new LectureReqDTO.Repair(2, "{}", List.of(
            new LectureReqDTO.Repair.Issue("node", "$.scenes[0]", "문제")
        ), 0);

        assertThatThrownBy(() ->
            service.generate(MEMBER_ID, req(resources.scenarioSchemaVersion(), repair, null)))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.REPAIR_NOT_ALLOWED);

        verify(openAiClient, never()).generateStructuredJson(any());
    }

    @Test
    @DisplayName("strict 스키마를 지원하지 않는 제공자는 거부한다")
    void unsupportedProvider() {
        LlmClient ollamaClient = mock(LlmClient.class);
        lenient().when(ollamaClient.getProvider()).thenReturn(LlmProvider.OLLAMA);
        lenient().when(ollamaClient.supportsStrictJsonSchema()).thenReturn(false);

        LectureGenerationService withOllama = new LectureGenerationService(
            List.of(openAiClient, ollamaClient), treeRepository, resources, MODEL, MAX_TOKENS);

        assertThatThrownBy(() -> withOllama.generate(
            MEMBER_ID, req(resources.scenarioSchemaVersion(), null, LlmProvider.OLLAMA)))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.UNSUPPORTED_PROVIDER);

        verify(ollamaClient, never()).generateStructuredJson(any());
    }

    @Test
    @DisplayName("provider 를 생략하면 OPENAI 로 호출한다")
    void defaultsToOpenAi() {
        when(openAiClient.generateStructuredJson(any())).thenReturn(Mono.just(success()));

        service.generate(MEMBER_ID, req(resources.scenarioSchemaVersion(), null, null));

        verify(openAiClient).generateStructuredJson(any());
    }

    @Test
    @DisplayName("응답이 잘렸으면 재시도하지 않고 바로 오류다")
    void truncatedResponseIsNotRetried() {
        when(openAiClient.generateStructuredJson(any()))
            .thenReturn(Mono.just(result(RESPONSE_TEXT, "length", null)));

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req()))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.RESPONSE_TRUNCATED);

        verify(openAiClient, times(1)).generateStructuredJson(any());
    }

    @Test
    @DisplayName("모델이 거절했으면 재시도하지 않고 바로 오류다")
    void refusalIsNotRetried() {
        when(openAiClient.generateStructuredJson(any()))
            .thenReturn(Mono.just(result(null, "stop", "답할 수 없는 요청입니다.")));

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req()))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.MODEL_REFUSED);

        verify(openAiClient, times(1)).generateStructuredJson(any());
    }

    @Test
    @DisplayName("거절도 아닌데 본문이 비어 있으면 오류다")
    void emptyContentIsError() {
        when(openAiClient.generateStructuredJson(any()))
            .thenReturn(Mono.just(result("  ", "stop", null)));

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req()))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.RESPONSE_EMPTY);
    }

    @Test
    @DisplayName("호출이 한 번 실패하면 재시도하고, 토큰은 모든 호출의 합이다")
    void retriesOnceThenSucceeds() {
        when(openAiClient.generateStructuredJson(any()))
            .thenReturn(Mono.error(new RuntimeException("연결 실패")))
            .thenReturn(Mono.just(success()));

        LectureGenResDTO res = service.generate(MEMBER_ID, req());

        assertThat(res.attemptCount()).isEqualTo(2);
        assertThat(res.responseText()).isEqualTo(RESPONSE_TEXT);
        // 실패한 호출에는 usage 가 없으므로 성공한 호출의 값만 더해진다.
        assertThat(res.usage().inputTokens()).isEqualTo(1200);
        verify(openAiClient, times(2)).generateStructuredJson(any());
    }

    @Test
    @DisplayName("재시도까지 실패하면 호출 실패로 응답한다")
    void failsAfterRetry() {
        when(openAiClient.generateStructuredJson(any()))
            .thenReturn(Mono.error(new RuntimeException("연결 실패")));

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req()))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.LLM_CALL_FAILED);

        verify(openAiClient, times(2)).generateStructuredJson(any());
    }

    @Test
    @DisplayName("타임아웃은 타임아웃 오류로 구분한다")
    void timeoutIsClassified() {
        when(openAiClient.generateStructuredJson(any()))
            .thenReturn(Mono.error(new RuntimeException(new TimeoutException("timeout"))));

        assertThatThrownBy(() -> service.generate(MEMBER_ID, req()))
            .isInstanceOf(LectureException.class)
            .hasFieldOrPropertyWithValue("errorCode", LectureErrorCode.LLM_TIMEOUT);

        verify(openAiClient, times(2)).generateStructuredJson(any());
    }

    @Test
    @DisplayName("응답이 비어 있으면 재시도한다")
    void retriesOnEmptyMono() {
        when(openAiClient.generateStructuredJson(any()))
            .thenReturn(Mono.empty())
            .thenReturn(Mono.just(success()));

        LectureGenResDTO res = service.generate(MEMBER_ID, req());

        assertThat(res.attemptCount()).isEqualTo(2);
    }

    private StructuredJsonRequest captureRequest() {
        ArgumentCaptor<StructuredJsonRequest> captor = ArgumentCaptor.forClass(StructuredJsonRequest.class);
        verify(openAiClient).generateStructuredJson(captor.capture());
        return captor.getValue();
    }
}

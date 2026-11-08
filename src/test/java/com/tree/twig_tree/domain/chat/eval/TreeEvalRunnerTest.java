package com.tree.twig_tree.domain.chat.eval;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.StructuredJsonResult;
import com.tree.twig_tree.domain.chat.eval.TreeEvalRun.Outcome;
import com.tree.twig_tree.domain.chat.prompt.TreePrompt;
import com.tree.twig_tree.domain.chat.service.TreeMetrics;
import com.tree.twig_tree.domain.chat.service.TreeValidator;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TreeEvalRunnerTest {

    private static final String TWO_LEVEL_TREE = """
        {"root":{"name":"루트","memo":"설명","children":[{"name":"자식","memo":null,"children":[]}]}}
        """;
    private static final TreeEvalCase CASE = new TreeEvalCase("case", "topic", "요청", null, 2, 2, 5);

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private LlmClient client;
    private TreeEvalRunner runner;

    @BeforeEach
    void setUp() {
        client = mock(LlmClient.class);
        runner = new TreeEvalRunner(client, mapper, new TreeValidator(), null);
    }

    private void respond(String content, String finishReason, String refusal) {
        when(client.generateTreeJson(anyString())).thenReturn(Mono.just(
            new StructuredJsonResult(content, "gpt-test", finishReason, refusal, 10, 20)));
    }

    @Test
    @DisplayName("정상 응답은 성공으로 분류하고 지표와 준수 여부를 계산한다")
    void success() {
        respond(TWO_LEVEL_TREE, "stop", null);

        TreeEvalRun run = runner.run(CASE, 1);

        assertThat(run.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(run.model()).isEqualTo("gpt-test");
        assertThat(run.metrics().nodeCount()).isEqualTo(2);
        assertThat(run.depthMatched()).isTrue();
        assertThat(run.nodesInRange()).isTrue();
    }

    @Test
    @DisplayName("판정 기준이 없는 케이스의 준수 여부는 null 이다")
    void noExpectation() {
        respond(TWO_LEVEL_TREE, "stop", null);

        TreeEvalRun run = runner.run(new TreeEvalCase("c", "topic", "요청", null, null, null, null), 1);

        assertThat(run.depthMatched()).isNull();
        assertThat(run.nodesInRange()).isNull();
    }

    @Test
    @DisplayName("잘림은 파싱 가능 여부보다 먼저 분류하고 준수 실패로 센다")
    void truncated() {
        respond(TWO_LEVEL_TREE, "length", null);

        TreeEvalRun run = runner.run(CASE, 1);

        assertThat(run.outcome()).isEqualTo(Outcome.TRUNCATED);
        assertThat(run.depthMatched()).isFalse();
    }

    @Test
    @DisplayName("거절, 빈 응답, 파싱 실패를 구분한다")
    void unusableResponses() {
        respond(null, "stop", "거절");
        assertThat(runner.run(CASE, 1).outcome()).isEqualTo(Outcome.REFUSED);

        respond(" ", "stop", null);
        assertThat(runner.run(CASE, 1).outcome()).isEqualTo(Outcome.EMPTY);

        respond("JSON 아님", "stop", null);
        assertThat(runner.run(CASE, 1).outcome()).isEqualTo(Outcome.PARSE_FAILED);
    }

    @Test
    @DisplayName("서버 검증에서 거부된 트리도 지표는 남긴다")
    void validationFailureKeepsMetrics() {
        String longName = "가".repeat(TreePrompt.NAME_MAX_LENGTH + 1);
        respond("{\"root\":{\"name\":\"" + longName + "\",\"memo\":null,\"children\":[]}}", "stop", null);

        TreeEvalRun run = runner.run(CASE, 1);

        assertThat(run.outcome()).isEqualTo(Outcome.VALIDATION_FAILED);
        assertThat(run.metrics().nodeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("유효하지 않은 응답은 한 번 재생성하고 토큰을 합산하며, 거절은 재생성하지 않는다")
    void retriesLikeProduction() {
        when(client.generateTreeJson(anyString())).thenReturn(
            Mono.just(new StructuredJsonResult("JSON 아님", "gpt-test", "stop", null, 10, 20)),
            Mono.just(new StructuredJsonResult(TWO_LEVEL_TREE, "gpt-test", "stop", null, 10, 20)));

        TreeEvalRun retried = runner.run(CASE, 1);

        assertThat(retried.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(retried.attempts()).isEqualTo(2);
        assertThat(retried.outputTokens()).isEqualTo(40);

        respond(null, "stop", "거절");
        assertThat(runner.run(CASE, 1).attempts()).isEqualTo(1);
    }

    @Test
    @DisplayName("WebClient 응답 타임아웃과 일반 호출 실패를 구분한다")
    void callFailures() {
        when(client.generateTreeJson(anyString()))
            .thenReturn(Mono.error(new RuntimeException("wrapped", ReadTimeoutException.INSTANCE)));
        assertThat(runner.run(CASE, 1).outcome()).isEqualTo(Outcome.TIMEOUT);

        when(client.generateTreeJson(anyString())).thenReturn(Mono.error(new IllegalStateException("500")));
        TreeEvalRun failed = runner.run(CASE, 1);
        assertThat(failed.outcome()).isEqualTo(Outcome.CALL_FAILED);
        assertThat(failed.error()).contains("500");
        assertThat(failed.nodesInRange()).isFalse();
    }

    @Test
    @DisplayName("평가 세트는 id 가 유일하고 문서가 존재하며 필수 카테고리를 포함한다")
    void bundledCasesAreValid() {
        List<TreeEvalCase> cases = TreeEvalCases.load(mapper);

        assertThat(new HashSet<>(cases.stream().map(TreeEvalCase::id).toList())).hasSameSizeAs(cases);
        assertThat(cases.stream().map(TreeEvalCase::category).collect(Collectors.toSet()))
            .contains("topic", "depth", "document");
        assertThat(cases.stream().filter(c -> c.category().equals("depth")).map(TreeEvalCase::expectedDepth))
            .contains(3, 5, 10, 15);
        cases.forEach(c -> {
            assertThat(c.message() != null || c.documentFile() != null).as(c.id()).isTrue();
            if (c.documentFile() != null) {
                assertThat(TreeEvalCases.document(c)).as(c.id()).isNotBlank();
            }
        });
    }

    @Test
    @DisplayName("리포트는 실패를 준수율 분모에 포함하고 평균은 성공만으로 계산한다")
    void reportAggregates() {
        TreeMetrics metrics = new TreeMetrics(20, 4, 12, 2, 1, 0, 10);
        List<TreeEvalRun> runs = List.of(
            new TreeEvalRun("a", "topic", 1, 2, Outcome.SUCCESS, "m", "stop", 1, 100, 2000, metrics, true, true,
                null, "{}", null),
            new TreeEvalRun("a", "topic", 2, 1, Outcome.TIMEOUT, null, null, 0, 0, 60000,
                TreeMetrics.from(null), false, false, null, null, "timeout"));

        String report = TreeEvalReport.render("m", runs);

        assertThat(report)
            .contains("| topic | 2 | 50.0% | 0 | 0 | 0 | 0 | 1 | 0 | 100 | 31.0 | 60.0 | 1 |")
            .contains("| topic | 50.0% | 50.0% | 20.0 | 4.0 | 10.0% | 1.00 | 0.00 | 50.0% |")
            .doesNotContain("LLM 채점");
    }
}

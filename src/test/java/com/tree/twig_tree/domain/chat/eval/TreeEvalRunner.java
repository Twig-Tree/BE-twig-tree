package com.tree.twig_tree.domain.chat.eval;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.StructuredJsonResult;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.eval.TreeEvalRun.Outcome;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.prompt.TreePrompt;
import com.tree.twig_tree.domain.chat.service.ChatService;
import com.tree.twig_tree.domain.chat.service.TreeMetrics;
import com.tree.twig_tree.domain.chat.service.TreeValidator;
import io.netty.handler.timeout.ReadTimeoutException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 케이스 하나를 운영과 같은 프롬프트·클라이언트·검증기로 실행하고 결과를 분류한다.
 *
 * <p>프롬프트를 복사하지 않고 {@link TreePrompt} 를 직접 써서, 평가 대상과 운영 동작이 어긋나지 않게 한다.
 * 재생성 정책도 {@link ChatService} 와 같고, DB 저장만 하지 않는다.
 */
final class TreeEvalRunner {

    private final LlmClient client;
    private final ObjectMapper mapper;
    private final TreeValidator validator;
    private final TreeEvalJudge judge;

    /** @param judge 의미 품질 채점기. 채점하지 않으면 {@code null} */
    TreeEvalRunner(LlmClient client, ObjectMapper mapper, TreeValidator validator, TreeEvalJudge judge) {
        this.client = client;
        this.mapper = mapper;
        this.validator = validator;
        this.judge = judge;
    }

    TreeEvalRun run(TreeEvalCase evalCase, int repeat) {
        String document = TreeEvalCases.document(evalCase);
        String prompt = TreePrompt.userMessage(evalCase.message(), document);

        long startedAt = System.nanoTime();
        long inputTokens = 0;
        long outputTokens = 0;

        for (int attempt = 1; ; attempt++) {
            StructuredJsonResult result;
            try {
                Duration timeout = ChatService.attemptTimeout(Duration.ofNanos(System.nanoTime() - startedAt));
                result = client.generateTreeJson(prompt).timeout(timeout).block();
            } catch (RuntimeException e) {
                Outcome outcome = isTimeout(e) ? Outcome.TIMEOUT : Outcome.CALL_FAILED;
                return failure(evalCase, repeat, attempt, outcome, inputTokens, outputTokens,
                    elapsedMs(startedAt), e.toString());
            }

            if (result == null) {
                return failure(evalCase, repeat, attempt, Outcome.EMPTY, inputTokens, outputTokens,
                    elapsedMs(startedAt), null);
            }
            inputTokens += result.inputTokens();
            outputTokens += result.outputTokens();

            LlmTreeDTO tree = parse(result.content());
            Outcome outcome = classify(result, tree);
            if (outcome != Outcome.SUCCESS && outcome != Outcome.REFUSED
                && attempt < ChatService.MAX_GENERATION_ATTEMPTS) {
                continue;
            }

            TreeMetrics metrics = TreeMetrics.from(tree);
            TreeEvalJudgement judgement = outcome == Outcome.SUCCESS && judge != null
                ? judge.judge(evalCase.message(), document, tree)
                : null;

            return new TreeEvalRun(
                evalCase.id(), evalCase.category(), repeat, attempt, outcome,
                result.model(), result.finishReason(), inputTokens, outputTokens, elapsedMs(startedAt),
                metrics,
                depthMatched(evalCase, outcome, metrics),
                nodesInRange(evalCase, outcome, metrics),
                judgement, result.content(), null
            );
        }
    }

    private Outcome classify(StructuredJsonResult result, LlmTreeDTO tree) {
        if (result.isTruncated()) {
            return Outcome.TRUNCATED;
        }
        if (result.isRefused()) {
            return Outcome.REFUSED;
        }
        if (result.hasEmptyContent()) {
            return Outcome.EMPTY;
        }
        if (tree == null) {
            return Outcome.PARSE_FAILED;
        }
        try {
            validator.validate(tree);
            return Outcome.SUCCESS;
        } catch (ChatException e) {
            return Outcome.VALIDATION_FAILED;
        }
    }

    private LlmTreeDTO parse(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(content, LlmTreeDTO.class);
        } catch (JacksonException e) {
            return null;
        }
    }

    private static Boolean depthMatched(TreeEvalCase evalCase, Outcome outcome, TreeMetrics metrics) {
        if (evalCase.expectedDepth() == null) {
            return null;
        }
        return outcome == Outcome.SUCCESS && metrics.maxDepth() == evalCase.expectedDepth();
    }

    private static Boolean nodesInRange(TreeEvalCase evalCase, Outcome outcome, TreeMetrics metrics) {
        if (evalCase.minNodes() == null && evalCase.maxNodes() == null) {
            return null;
        }
        boolean aboveMin = evalCase.minNodes() == null || metrics.nodeCount() >= evalCase.minNodes();
        boolean belowMax = evalCase.maxNodes() == null || metrics.nodeCount() <= evalCase.maxNodes();
        return outcome == Outcome.SUCCESS && aboveMin && belowMax;
    }

    private static TreeEvalRun failure(TreeEvalCase evalCase, int repeat, int attempts, Outcome outcome,
                                       long inputTokens, long outputTokens, long latencyMs, String error) {
        TreeMetrics empty = TreeMetrics.from(null);
        return new TreeEvalRun(
            evalCase.id(), evalCase.category(), repeat, attempts, outcome,
            null, null, inputTokens, outputTokens, latencyMs,
            empty,
            depthMatched(evalCase, outcome, empty),
            nodesInRange(evalCase, outcome, empty),
            null, null, error
        );
    }

    /** WebClient 의 응답 타임아웃(Netty)과 Reactor 의 타임아웃은 예외 타입이 달라 둘 다 본다. */
    private static boolean isTimeout(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof TimeoutException || cause instanceof ReadTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static long elapsedMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}

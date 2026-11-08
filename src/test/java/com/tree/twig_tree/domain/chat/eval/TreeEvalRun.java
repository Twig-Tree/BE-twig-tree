package com.tree.twig_tree.domain.chat.eval;

import com.tree.twig_tree.domain.chat.service.TreeMetrics;

/**
 * 케이스 1회 실행 결과.
 *
 * @param attempts     LLM 호출 횟수. 토큰과 지연은 모든 시도의 합이다
 * @param depthMatched 깊이 판정 결과. 판정 대상이 아니면 {@code null}. 실패한 실행은 미준수다
 * @param nodesInRange 노드 수 판정 결과. 판정 대상이 아니면 {@code null}. 실패한 실행은 미준수다
 * @param content      모델 응답 원문. 사람이나 채점기가 다시 볼 수 있게 남긴다
 * @param error        호출 실패 메시지. 없으면 {@code null}
 */
public record TreeEvalRun(
    String caseId,
    String category,
    int repeat,
    int attempts,
    Outcome outcome,
    String model,
    String finishReason,
    long inputTokens,
    long outputTokens,
    long latencyMs,
    TreeMetrics metrics,
    Boolean depthMatched,
    Boolean nodesInRange,
    TreeEvalJudgement judgement,
    String content,
    String error
) {

    public enum Outcome {
        SUCCESS,
        TRUNCATED,
        REFUSED,
        EMPTY,
        PARSE_FAILED,
        VALIDATION_FAILED,
        TIMEOUT,
        CALL_FAILED
    }

    boolean isSuccess() {
        return outcome == Outcome.SUCCESS;
    }
}

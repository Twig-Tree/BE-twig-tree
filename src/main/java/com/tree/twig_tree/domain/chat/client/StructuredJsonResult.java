package com.tree.twig_tree.domain.chat.client;

/**
 * JSON Schema 호출의 응답.
 *
 * <p>본문을 파싱하지 않고 그대로 담는다. 응답이 쓸 만한지는 호출한 도메인이 판단한다.
 *
 * @param content      모델 응답 본문({@code message.content}). 거절했다면 {@code null}
 * @param model        응답에 실린 모델 이름(스냅샷). 평가에서 모델별로 비교할 때 쓴다
 * @param finishReason 응답이 끝난 이유. {@code "length"} 면 상한에 걸려 잘린 응답이다
 * @param refusal      모델이 거절한 이유({@code message.refusal}). 없으면 {@code null}
 * @param inputTokens  {@code usage.prompt_tokens}
 * @param outputTokens {@code usage.completion_tokens}
 */
public record StructuredJsonResult(
    String content,
    String model,
    String finishReason,
    String refusal,
    long inputTokens,
    long outputTokens
) {

    /** 응답이 상한에 걸려 잘렸는지. */
    public boolean isTruncated() {
        return "length".equals(finishReason);
    }

    /** 모델이 응답을 거절했는지. */
    public boolean isRefused() {
        return refusal != null && !refusal.isBlank();
    }

    /** 거절도 아니면서 본문이 비어 있는지. */
    public boolean hasEmptyContent() {
        return content == null || content.isBlank();
    }
}

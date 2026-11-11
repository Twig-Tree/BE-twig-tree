package com.tree.twig_tree.domain.chat.client;

import reactor.core.publisher.Mono;

public interface LlmClient {

    /** 단일 사용자 메시지를 보내고 평문 응답을 받는다. */
    Mono<String> chat(String message);

    /**
     * 트리 생성 전용 호출. system 프롬프트와 응답 스키마를 적용해
     * {@link com.tree.twig_tree.domain.chat.prompt.TreePrompt#RESPONSE_SCHEMA} 형태의 JSON 을 본문으로 돌려준다.
     *
     * <p>잘림·거절과 토큰 사용량을 호출부에서 구분할 수 있도록 본문만이 아니라 응답 메타데이터를 함께 담는다.
     */
    Mono<StructuredJsonResult> generateTreeJson(String message);

    /**
     * JSON Schema 를 강제하는 strict 모드를 지원하는지.
     *
     * <p>지원하지 않는 제공자로 {@link #generateStructuredJson} 을 부르지 않도록, 호출하는 쪽이
     * 먼저 확인한다. 스키마를 강제할 수 없는 제공자는 응답 모양을 보장하지 못해 쓸 수 없다.
     */
    boolean supportsStrictJsonSchema();

    /**
     * 주어진 JSON Schema 에 맞는 응답을 받는 호출.
     *
     * <p>프롬프트와 스키마는 호출하는 쪽이 정하고, 이 메서드는 응답을 파싱하지 않고 그대로 담아 돌려준다.
     *
     * @throws UnsupportedOperationException {@link #supportsStrictJsonSchema()} 가 {@code false} 인 제공자
     */
    Mono<StructuredJsonResult> generateStructuredJson(StructuredJsonRequest request);

    LlmProvider getProvider();
}

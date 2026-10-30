package com.tree.twig_tree.domain.chat.client;

import reactor.core.publisher.Mono;

public interface LlmClient {

    /** 단일 사용자 메시지를 보내고 평문 응답을 받는다. */
    Mono<String> chat(String message);

    /**
     * 트리 생성 전용 호출. system 프롬프트와 JSON 응답 강제를 적용해
     * {@link com.tree.twig_tree.domain.chat.prompt.TreePrompt} 스키마의 JSON 문자열을 반환한다.
     */
    Mono<String> generateTreeJson(String message);

    LlmProvider getProvider();
}

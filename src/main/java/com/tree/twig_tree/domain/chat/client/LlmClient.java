package com.tree.twig_tree.domain.chat.client;

import reactor.core.publisher.Mono;

public interface LlmClient {
    Mono<String> chat(String message);
    LlmProvider getProvider();
}

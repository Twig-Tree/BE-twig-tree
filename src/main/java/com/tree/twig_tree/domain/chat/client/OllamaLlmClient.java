package com.tree.twig_tree.domain.chat.client;

import com.tree.twig_tree.domain.chat.prompt.TreePrompt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Component
public class OllamaLlmClient implements LlmClient {

    private final WebClient ollamaWebClient;

    @Value("${ollama.model}")
    private String model;

    public OllamaLlmClient(@Qualifier("ollamaWebClient") WebClient ollamaWebClient) {
        this.ollamaWebClient = ollamaWebClient;
    }

    @Override
    public Mono<String> chat(String message) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", message)),
            "stream", false
        );

        return callChat(body);
    }

    @Override
    public Mono<String> generateTreeJson(String message) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", TreePrompt.SYSTEM),
                Map.of("role", "user", "content", message)
            ),
            // Ollama 는 format: "json" 으로 JSON 응답을 강제한다.
            "format", "json",
            "stream", false
        );

        return callChat(body);
    }

    private Mono<String> callChat(Map<String, Object> body) {
        return ollamaWebClient.post()
            .uri("/api/chat")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(OllamaResponse.class)
            .map(res -> res.message().content());
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.OLLAMA;
    }

    private record OllamaResponse(OllamaMessage message) {
        private record OllamaMessage(String content) {}
    }
}

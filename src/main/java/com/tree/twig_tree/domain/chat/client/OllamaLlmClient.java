package com.tree.twig_tree.domain.chat.client;

import com.fasterxml.jackson.annotation.JsonProperty;
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

        return callChat(body).map(res -> res.message().content());
    }

    @Override
    public Mono<StructuredJsonResult> generateTreeJson(String message) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", TreePrompt.SYSTEM),
                Map.of("role", "user", "content", message)
            ),
            // format 에 스키마를 넘겨 응답 구조를 유도한다. OpenAI strict 와 같은 보장은 아니어서 서버 검증이 필요하다.
            "format", TreePrompt.RESPONSE_SCHEMA,
            "stream", false
        );

        return callChat(body).map(OllamaLlmClient::toResult);
    }

    @Override
    public boolean supportsStrictJsonSchema() {
        // Ollama 는 JSON Schema 를 강제하는 strict 모드가 없어 응답 모양을 보장할 수 없다.
        return false;
    }

    @Override
    public Mono<StructuredJsonResult> generateStructuredJson(StructuredJsonRequest request) {
        throw new UnsupportedOperationException("Ollama 는 strict JSON Schema 를 지원하지 않습니다.");
    }

    private Mono<OllamaResponse> callChat(Map<String, Object> body) {
        return ollamaWebClient.post()
            .uri("/api/chat")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(OllamaResponse.class);
    }

    private static StructuredJsonResult toResult(OllamaResponse res) {
        return new StructuredJsonResult(
            res.message().content(),
            res.model(),
            res.doneReason(),
            null,
            res.promptEvalCount() == null ? 0L : res.promptEvalCount(),
            res.evalCount() == null ? 0L : res.evalCount()
        );
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.OLLAMA;
    }

    private record OllamaResponse(
        String model,
        OllamaMessage message,
        @JsonProperty("done_reason") String doneReason,
        @JsonProperty("prompt_eval_count") Long promptEvalCount,
        @JsonProperty("eval_count") Long evalCount
    ) {
        private record OllamaMessage(String content) {}
    }
}

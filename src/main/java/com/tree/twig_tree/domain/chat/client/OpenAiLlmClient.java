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
public class OpenAiLlmClient implements LlmClient {

    private final WebClient openAiWebClient;

    @Value("${openai.model}")
    private String model;

    public OpenAiLlmClient(@Qualifier("openAiWebClient") WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
    }

    @Override
    public Mono<String> chat(String message) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", message))
        );

        return callChatCompletions(body);
    }

    @Override
    public Mono<String> generateTreeJson(String message) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", TreePrompt.SYSTEM),
                Map.of("role", "user", "content", message)
            ),
            // JSON 이외의 텍스트가 섞이지 않도록 강제해 파싱 실패를 줄인다.
            "response_format", Map.of("type", "json_object"),
            "temperature", 0.4
        );

        return callChatCompletions(body);
    }

    private Mono<String> callChatCompletions(Map<String, Object> body) {
        return openAiWebClient.post()
            .uri("/chat/completions")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(OpenAiResponse.class)
            .map(res -> res.choices().get(0).message().content());
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.OPENAI;
    }

    private record OpenAiResponse(List<Choice> choices) {
        private record Choice(Message message) {}
        private record Message(String content) {}
    }
}

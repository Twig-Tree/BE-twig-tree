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
public class OpenAiLlmClient implements LlmClient {

    private final WebClient openAiWebClient;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.tree-max-tokens}")
    private int treeMaxTokens;

    public OpenAiLlmClient(@Qualifier("openAiWebClient") WebClient openAiWebClient) {
        this.openAiWebClient = openAiWebClient;
    }

    @Override
    public Mono<String> chat(String message) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", message))
        );

        return callChatCompletions(body).map(OpenAiLlmClient::firstContent);
    }

    @Override
    public Mono<String> generateTreeJson(String message) {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", TreePrompt.SYSTEM),
                Map.of("role", "user", "content", message)
            ),
            // strict 모드는 응답 구조가 스키마를 벗어나지 않게 모델 쪽에서 강제한다.
            "response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                    "name", TreePrompt.RESPONSE_SCHEMA_NAME,
                    "strict", true,
                    "schema", TreePrompt.RESPONSE_SCHEMA
                )
            ),
            "max_completion_tokens", treeMaxTokens,
            "temperature", 0.4
        );

        // 모델이 거절하면 content 가 null 이다. 빈 응답으로 넘겨 호출부의 응답 검증에서 걸러지게 한다.
        return callChatCompletions(body).mapNotNull(OpenAiLlmClient::firstContent);
    }

    @Override
    public boolean supportsStrictJsonSchema() {
        return true;
    }

    @Override
    public Mono<StructuredJsonResult> generateStructuredJson(StructuredJsonRequest request) {
        Map<String, Object> body = Map.of(
            "model", request.model(),
            "messages", request.messages().stream()
                .map(m -> Map.<String, Object>of("role", m.role(), "content", m.content()))
                .toList(),
            // strict 모드는 응답이 스키마를 벗어나지 않게 모델 쪽에서 강제한다.
            "response_format", Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                    "name", request.schemaName(),
                    "strict", true,
                    "schema", request.schema()
                )
            ),
            // 상한이 없으면 큰 트리에서 응답이 길어져 비용과 대기 시간이 함께 늘어난다.
            "max_completion_tokens", request.maxOutputTokens()
        );

        return callChatCompletions(body).map(OpenAiLlmClient::toStructuredResult);
    }

    private Mono<OpenAiResponse> callChatCompletions(Map<String, Object> body) {
        return openAiWebClient.post()
            .uri("/chat/completions")
            .bodyValue(body)
            .retrieve()
            .bodyToMono(OpenAiResponse.class);
    }

    private static String firstContent(OpenAiResponse response) {
        return firstChoice(response).message().content();
    }

    private static StructuredJsonResult toStructuredResult(OpenAiResponse response) {
        OpenAiResponse.Choice choice = firstChoice(response);
        OpenAiResponse.Usage usage = response.usage();

        return new StructuredJsonResult(
            choice.message().content(),
            response.model(),
            choice.finishReason(),
            choice.message().refusal(),
            usage == null || usage.promptTokens() == null ? 0L : usage.promptTokens(),
            usage == null || usage.completionTokens() == null ? 0L : usage.completionTokens()
        );
    }

    /**
     * 응답에서 첫 choice 를 꺼낸다.
     *
     * <p>choices 가 비어 있으면 모델 응답이 없는 것이므로 호출 실패로 다룬다. 호출하는 쪽이
     * {@code RuntimeException} 을 호출 실패로 분류하므로 재시도 대상이 된다.
     */
    private static OpenAiResponse.Choice firstChoice(OpenAiResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new IllegalStateException("OpenAI 응답에 choices 가 없습니다.");
        }
        return response.choices().get(0);
    }

    @Override
    public LlmProvider getProvider() {
        return LlmProvider.OPENAI;
    }

    private record OpenAiResponse(String model, List<Choice> choices, Usage usage) {

        private record Choice(
            Message message,
            @JsonProperty("finish_reason") String finishReason
        ) {}

        private record Message(String content, String refusal) {}

        private record Usage(
            @JsonProperty("prompt_tokens") Long promptTokens,
            @JsonProperty("completion_tokens") Long completionTokens
        ) {}
    }
}

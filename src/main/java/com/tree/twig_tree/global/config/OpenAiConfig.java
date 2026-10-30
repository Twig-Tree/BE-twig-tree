package com.tree.twig_tree.global.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class OpenAiConfig {

    /** 연결 타임아웃(ms). */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    /** 응답 타임아웃. LLM 은 응답이 느릴 수 있어 넉넉히 잡되 무한 대기는 막는다. */
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    public WebClient openAiWebClient(
        @Value("${openai.api-key}") String apiKey,
        @Value("${openai.base-url}") String baseUrl) {

        HttpClient httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
            .responseTimeout(RESPONSE_TIMEOUT);

        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}

package com.tree.twig_tree.domain.chat.client;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * JSON Schema 를 강제하는 LLM 호출 요청.
 *
 * <p>프롬프트와 스키마를 호출하는 쪽이 모두 정해서 넘기므로, 클라이언트는 전송 형식만 맞춘다.
 * 트리 생성({@link LlmClient#generateTreeJson})과 달리 도메인 지식을 갖지 않는다.
 *
 * @param model           모델 이름. 도메인마다 다른 모델을 쓸 수 있어 요청에 담는다
 * @param maxOutputTokens 응답 토큰 상한. 비용과 응답 시간을 묶어 두기 위해 반드시 지정한다
 * @param schemaName      스키마 이름. OpenAI 가 응답 검증에 쓰는 식별자
 * @param schema          strict 모드에 넘길 JSON Schema
 * @param messages        system/user/assistant 메시지. 넘긴 순서대로 전송한다
 */
public record StructuredJsonRequest(
    String model,
    int maxOutputTokens,
    String schemaName,
    JsonNode schema,
    List<Message> messages
) {

    public record Message(String role, String content) {

        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }
}

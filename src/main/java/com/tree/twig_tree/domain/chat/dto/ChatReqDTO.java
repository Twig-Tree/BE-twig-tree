package com.tree.twig_tree.domain.chat.dto;

import com.tree.twig_tree.domain.chat.client.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "채팅 요청 DTO (현재 mock 모드에서는 무시됨)")
public record ChatReqDTO(

    @Schema(description = "사용자 메시지", example = "자료구조 트리 만들어줘")
    String message,

    @Schema(description = "LLM 제공자", example = "OPENAI")
    LlmProvider provider
) {
}

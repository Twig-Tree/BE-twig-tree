package com.tree.twig_tree.domain.chat.dto;

import com.tree.twig_tree.domain.chat.client.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

@Schema(description = "트리 생성 요청 DTO")
public record ChatReqDTO(

    @Schema(description = "사용자 메시지", example = "자료구조 트리 만들어줘", maxLength = MESSAGE_MAX_LENGTH)
    @Size(max = MESSAGE_MAX_LENGTH)
    String message,

    @Schema(description = "LLM 제공자", example = "OPENAI")
    LlmProvider provider
) {

    /**
     * 지시문 길이 상한.
     *
     * <p>제한이 없으면 임의로 큰 프롬프트가 그대로 LLM 에 실려 토큰 비용과 응답 대기가 늘어난다.
     * 파일 업로드 경로에는 붙일 DTO 가 없어 {@code ChatService} 가 같은 상수로 직접 검사한다.
     */
    public static final int MESSAGE_MAX_LENGTH = 500;
}

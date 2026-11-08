package com.tree.twig_tree.domain.lecture.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * 강의 시나리오 생성 응답 DTO.
 *
 * <p>{@code responseText} 는 모델 응답을 파싱하지 않고 그대로 담는다. 시나리오 검사와 컴파일은
 * 프론트엔드가 하고, 실패하면 {@code repair} 로 다시 요청한다.
 */
@Builder
@Schema(description = "강의 시나리오 생성 응답 DTO")
public record LectureGenResDTO(

    @Schema(description = "모델 응답 본문. 파싱하지 않은 원본")
    String responseText,

    @Schema(description = "응답에 실린 모델 이름(스냅샷)", example = "gpt-4o-mini-2024-07-18")
    String model,

    @Schema(description = "프롬프트 버전. 평가에서 모델별·프롬프트별로 결과를 나누는 데 쓴다", example = "lecture-v1")
    String promptVersion,

    @Schema(description = "토큰 사용량. 재시도했다면 모든 호출의 합")
    Usage usage,

    @Schema(description = "모델을 부른 횟수(재시도 포함)", example = "1")
    int attemptCount
) {

    @Builder
    @Schema(description = "토큰 사용량")
    public record Usage(

        @Schema(description = "입력 토큰", example = "1200")
        long inputTokens,

        @Schema(description = "출력 토큰", example = "340")
        long outputTokens
    ) {}
}

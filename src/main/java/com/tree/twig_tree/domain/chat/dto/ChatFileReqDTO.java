package com.tree.twig_tree.domain.chat.dto;

import com.tree.twig_tree.domain.chat.client.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 업로드 기반 트리 생성 요청 DTO (multipart/form-data).
 *
 * <p>multipart 는 {@code @RequestBody} 로 받을 수 없어 {@code @ModelAttribute} 로 바인딩한다.
 * 덕분에 JSON 경로의 {@link ChatReqDTO} 와 같은 {@code @Valid} 검증 흐름을 탄다.
 */
@Schema(description = "파일 업로드 트리 생성 요청 DTO")
public record ChatFileReqDTO(

    @Schema(description = "(선택) txt, md, pdf, docx, hwp, hwpx 파일", type = "string", format = "binary")
    MultipartFile file,

    @Schema(description = "(선택) 추가 지시문. 500자 이내", example = "3단계 깊이로 정리해줘",
        maxLength = ChatReqDTO.MESSAGE_MAX_LENGTH)
    @Size(max = ChatReqDTO.MESSAGE_MAX_LENGTH)
    String message,

    @Schema(description = "(선택) LLM 제공자. 생략 시 OPENAI", example = "OPENAI")
    LlmProvider provider
) {
}

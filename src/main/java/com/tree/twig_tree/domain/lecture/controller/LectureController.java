package com.tree.twig_tree.domain.lecture.controller;

import com.tree.twig_tree.domain.lecture.dto.LectureGenResDTO;
import com.tree.twig_tree.domain.lecture.dto.LectureReqDTO;
import com.tree.twig_tree.domain.lecture.exception.code.LectureErrorCode;
import com.tree.twig_tree.domain.lecture.exception.code.LectureSuccessCode;
import com.tree.twig_tree.domain.lecture.service.LectureGenerationService;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.swagger.ApiErrorCodeExample;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 강의 시나리오 생성 컨트롤러.
 *
 * <p>트리와 사용자 요청을 받아 LLM 으로 강의 시나리오를 만들고, 모델 응답 본문을 그대로 반환한다.
 * 시나리오 검사와 컴파일은 프론트엔드가 한다.
 */
@Tag(name = "Lecture Generation", description = "LLM 기반 강의 시나리오 생성 API")
@RestController
@RequestMapping("/lecture-request")
@RequiredArgsConstructor
public class LectureController {

    private final LectureGenerationService lectureGenerationService;

    @Operation(
        summary = "강의 시나리오 생성 요청",
        description = """
            트리 outline 과 사용자 요청을 LLM 에 전달해 강의 시나리오(LectureScenario)를 생성합니다.
            생성한 시나리오는 저장하지 않습니다.

            - `responseText`: 모델 응답 본문을 **파싱하지 않은 원본**입니다. 검사와 컴파일은 프론트엔드가 합니다
            - `treeOutline`: 프론트엔드가 만든 outline. 한 줄에 `- [노드ID] 제목` 하나, 깊이마다 두 칸 들여쓰기
            - `scenarioSchemaVersion`: 서버가 가진 스키마 버전과 다르면 400 입니다
            - `repair`: 검사가 실패했을 때 직전 응답과 문제를 담아 다시 요청합니다. 최대 1회입니다

            응답이 길이 상한에 걸려 잘리거나(`finish_reason: "length"`) 모델이 거절한 경우는
            다시 불러도 같을 가능성이 높아 재시도하지 않고 502 로 응답합니다.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "200",
        content = @Content(schema = @Schema(implementation = LectureGenResDTO.class))
    )
    @ApiErrorCodeExample(value = TreeErrorCode.class, only = {"TREE_NOT_FOUND", "TREE_ACCESS_DENIED"})
    @ApiErrorCodeExample(value = LectureErrorCode.class,
        only = {"UNSUPPORTED_PROVIDER", "SCHEMA_VERSION_MISMATCH", "REPAIR_NOT_ALLOWED",
                "LLM_CALL_FAILED", "RESPONSE_TRUNCATED", "MODEL_REFUSED", "RESPONSE_EMPTY", "LLM_TIMEOUT"})
    @PostMapping
    public ResponseEntity<ApiResponse<LectureGenResDTO>> generateLectureScenario(
        @AuthenticationPrincipal Long memberId,
        @RequestBody @Valid LectureReqDTO req
    ) {
        LectureGenResDTO result = lectureGenerationService.generate(memberId, req);
        return ApiResponse.onSuccess(LectureSuccessCode.LECTURE_SCENARIO_GENERATED, result);
    }
}

package com.tree.twig_tree.domain.lecture.dto;

import com.tree.twig_tree.domain.chat.client.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 강의 시나리오 생성 요청 DTO.
 *
 * <p>형식은 프론트엔드의 입력 계약(twig-tree-web {@code docs/specs/lecture-generation.md})을 따른다.
 * 트리 outline 은 프론트엔드가 만들어 보낸다. 모델이 본 노드와 프론트엔드 컴파일러가 인정하는 노드를
 * 같게 두기 위해서이며, 백엔드는 길이 상한만 확인한다.
 */
@Schema(description = "강의 시나리오 생성 요청 DTO")
public record LectureReqDTO(

    @Schema(description = "강의를 만들 트리 ID", example = "12")
    @NotNull
    Long treeId,

    @Schema(description = "사용자 요청 문장", example = "포유류와 파충류가 어떻게 다른지 비교해서 설명해 줘.",
        maxLength = REQUEST_MAX_LENGTH)
    @NotBlank
    @Size(max = REQUEST_MAX_LENGTH)
    String request,

    @Schema(description = "프론트엔드가 만든 트리 outline. 한 줄에 `- [노드ID] 제목` 하나, 깊이마다 두 칸 들여쓰기",
        example = "- [1] 척추동물\n  - [2] 포유류\n  - [3] 파충류", maxLength = TREE_OUTLINE_MAX_LENGTH)
    @NotBlank
    @Size(max = TREE_OUTLINE_MAX_LENGTH)
    String treeOutline,

    @Schema(description = "프론트엔드가 기대하는 시나리오 스키마 버전. 서버가 가진 버전과 다르면 400", example = "1")
    @NotNull
    Integer scenarioSchemaVersion,

    @Schema(description = "(선택) repair 요청. 처음 요청이면 생략하거나 null")
    @Valid
    Repair repair,

    @Schema(description = "(선택) LLM 제공자. 생략 시 OPENAI", example = "OPENAI")
    LlmProvider provider
) {

    /** 요청 문장 길이 상한. 트리 생성({@code ChatReqDTO.MESSAGE_MAX_LENGTH})과 같은 값으로 둔다. */
    public static final int REQUEST_MAX_LENGTH = 500;

    /**
     * 트리 outline 길이 상한.
     *
     * <p>노드 100개 트리의 outline 이 약 5,000자이므로 여유를 두고 잡았다. 클라이언트가 만든 문자열이
     * 그대로 프롬프트에 실리므로, 상한이 없으면 임의로 큰 입력이 토큰 비용으로 이어진다.
     */
    public static final int TREE_OUTLINE_MAX_LENGTH = 20_000;

    /**
     * repair 에 싣는 직전 응답 길이 상한.
     *
     * <p>응답 토큰 상한({@code openai.lecture-max-tokens}, 기본 16,000)의 토큰당 약 6자로 잡았다.
     * 시나리오는 영문 키 위주의 JSON 이라 토큰당 글자 수가 높다. 모델이 끝까지 쓴 응답이 이 값을 넘으면
     * 그 응답의 repair 요청이 검증에서 거부되므로, 토큰 상한을 올리면 이 값도 함께 올린다.
     */
    public static final int PREVIOUS_RESPONSE_MAX_LENGTH = 100_000;

    /** repair 한 번에 보낼 수 있는 문제 수. 프론트엔드의 {@code MAX_LECTURE_REPAIR_ISSUES} 와 같다. */
    public static final int MAX_REPAIR_ISSUES = 20;

    /** 허용하는 repair 횟수. 프론트엔드의 {@code MAX_LECTURE_REPAIR_ATTEMPTS} 와 같고, 비용 상한 역할을 한다. */
    public static final int MAX_REPAIR_ATTEMPT = 1;

    /** 문제 경로 길이 상한. */
    public static final int ISSUE_PATH_MAX_LENGTH = 500;

    /** 문제 설명 길이 상한. */
    public static final int ISSUE_MESSAGE_MAX_LENGTH = 1_000;

    /**
     * repair 요청.
     *
     * <p>프론트엔드의 {@code getLectureRepairRequest} 가 만든다. 직전 응답을 함께 받아 백엔드가
     * 요청 사이에 상태를 갖지 않게 한다.
     */
    @Schema(description = "repair 요청")
    public record Repair(

        @Schema(description = "repair 시도 번호. 1 만 허용", example = "1")
        @NotNull
        Integer attempt,

        @Schema(description = "고쳐야 할 직전 모델 응답 본문", maxLength = PREVIOUS_RESPONSE_MAX_LENGTH)
        @NotBlank
        @Size(max = PREVIOUS_RESPONSE_MAX_LENGTH)
        String previousResponseText,

        @Schema(description = "프론트엔드가 찾은 문제 목록")
        @NotEmpty
        @Size(max = MAX_REPAIR_ISSUES)
        @Valid
        List<Issue> issues,

        @Schema(description = "(선택) 상한을 넘겨 보내지 못한 문제 수", example = "0")
        @PositiveOrZero
        Integer omittedIssueCount
    ) {

        @Schema(description = "시나리오 문제")
        public record Issue(

            @Schema(description = "문제를 찾은 단계", example = "node")
            @NotBlank
            @Size(max = 50)
            String stage,

            @Schema(description = "시나리오 안의 경로", example = "$.scenes[0].beats[0].cues[0].roles.subject",
                maxLength = ISSUE_PATH_MAX_LENGTH)
            @Size(max = ISSUE_PATH_MAX_LENGTH)
            String path,

            @Schema(description = "문제 설명", example = "트리에 없는 노드(999)입니다.",
                maxLength = ISSUE_MESSAGE_MAX_LENGTH)
            @NotBlank
            @Size(max = ISSUE_MESSAGE_MAX_LENGTH)
            String message
        ) {}
    }
}

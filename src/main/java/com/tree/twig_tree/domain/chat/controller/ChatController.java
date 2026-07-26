package com.tree.twig_tree.domain.chat.controller;

import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.code.ChatSuccessCode;
import com.tree.twig_tree.domain.chat.service.ChatService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;
import com.tree.twig_tree.global.mock.MockResponseLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;

/**
 * 트리 생성 컨트롤러.
 *
 * <p>기본 동작은 LLM 을 호출해 실제 트리를 생성/저장한다.
 * 프론트 개발용으로, {@code mock} 파라미터를 주면 미리 정의된 mock 트리를 그대로 반환한다.
 */
@Tag(name = "Tree Generation", description = "LLM 기반 트리 생성 API")
@RestController
@RequestMapping("/tree-request")
@RequiredArgsConstructor
public class ChatController {

    private static final String MOCK_PATH_PREFIX = "mocks/chat/tree-";
    private static final String MOCK_PATH_SUFFIX = ".json";

    private final ChatService chatService;
    private final MockResponseLoader mockResponseLoader;

    @Operation(
        summary = "트리 생성 요청",
        description = """
            사용자 메시지를 LLM 에 전달해 계층형 트리를 생성하고 DB 에 저장한 뒤,
            생성된 트리(treeId + nodes)를 반환합니다.

            요청 본문: `{ "message": "자료구조 트리 만들어줘", "provider": "OPENAI" }`

            프론트 개발용 mock:
            - `?mock=small` (노드 5개), `?mock=empty`, `?mock=large`, `?mock=max`
            - `mock` 파라미터가 있으면 LLM 을 호출하지 않고 미리 정의된 트리를 반환합니다.
            """
    )
    @PostMapping
    public ApiResponse<?> generateTree(
        @RequestBody(required = false) ChatReqDTO req,
        @Parameter(
            description = "(선택) mock 시나리오. 지정 시 LLM 을 호출하지 않고 mock 트리를 반환합니다.",
            schema = @Schema(allowableValues = {"empty", "small", "large", "max"})
        )
        @RequestParam(required = false) String mock
    ) {
        if (mock != null) {
            return mockResponse(mock);
        }

        TreeGenResDTO tree = chatService.generateTree(req);
        return ApiResponse.onSuccess(ChatSuccessCode.TREE_GENERATED, tree);
    }

    /**
     * 파일 입력 기반 트리 생성 (multipart/form-data).
     *
     * <p>같은 경로를 쓰되 Content-Type 으로 분기된다. JSON 요청은 위의 핸들러가 그대로 처리한다.
     */
    @Operation(
        summary = "트리 생성 요청 (파일 업로드)",
        description = """
            txt/md 파일을 업로드해 그 내용으로 트리를 생성합니다. `multipart/form-data` 로 요청하세요.

            - `file`: txt 또는 md 파일 (최대 1MB, 본문 20,000자 이내)
            - `message`: (선택) 추가 지시문. 예) "3단계 깊이로 정리해줘"
            - `provider`: LLM 제공자 (OPENAI / OLLAMA)

            `file` 과 `message` 는 각각 선택이지만 **둘 다 비어 있으면 400** 입니다.
            업로드한 파일은 저장하지 않고 요청 처리 중에만 사용합니다.
            """
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<?> generateTreeFromFile(
        @Parameter(description = "(선택) txt/md 파일")
        @RequestPart(value = "file", required = false) MultipartFile file,

        @Parameter(description = "(선택) 추가 지시문", example = "3단계 깊이로 정리해줘")
        @RequestParam(required = false) String message,

        @Parameter(description = "LLM 제공자", schema = @Schema(allowableValues = {"OPENAI", "OLLAMA"}))
        @RequestParam(required = false) LlmProvider provider,

        @Parameter(description = "(선택) mock 시나리오. 지정 시 LLM 을 호출하지 않습니다.")
        @RequestParam(required = false) String mock
    ) {
        if (mock != null) {
            return mockResponse(mock);
        }

        TreeGenResDTO tree = chatService.generateTree(provider, message, file);
        return ApiResponse.onSuccess(ChatSuccessCode.TREE_GENERATED, tree);
    }

    private ApiResponse<JsonNode> mockResponse(String scenario) {
        validateScenario(scenario);
        JsonNode treeData = mockResponseLoader.load(MOCK_PATH_PREFIX + scenario + MOCK_PATH_SUFFIX);
        return ApiResponse.onSuccess(ChatSuccessCode.TREE_GENERATED, treeData);
    }

    /**
     * 경로 탈출 공격 방지 — 소문자/숫자/하이픈만 허용.
     */
    private void validateScenario(String scenario) {
        if (!scenario.matches("[a-z0-9-]+")) {
            throw new ProjectException(GeneralErrorCode.BAD_REQUEST);
        }
    }
}

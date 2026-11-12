package com.tree.twig_tree.domain.chat.controller;

import com.tree.twig_tree.domain.chat.dto.ChatFileReqDTO;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.exception.code.ChatSuccessCode;
import com.tree.twig_tree.domain.chat.service.ChatService;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.swagger.ApiErrorCodeExample;
import com.tree.twig_tree.global.mock.MockResponseLoader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 트리 생성 컨트롤러.
 *
 * <p>기본 동작은 LLM 을 호출해 실제 트리를 생성/저장한다.
 * 프론트 개발용으로, {@code mock} 파라미터를 주면 미리 정의된 mock 트리를 그대로 반환한다.
 *
 * <p>경로는 두 가지다. {@code /tree-request} 는 트리를 담을 워크스페이스를 새로 만들고,
 * {@code /workspaces/{workspaceId}/tree-request} 는 사용자가 이미 만들어 둔 빈 워크스페이스를 채운다.
 * 두 경로 모두 JSON 과 {@code multipart/form-data} 요청을 Content-Type 으로 분기해 받는다.
 */
@Tag(name = "Tree Generation", description = "LLM 기반 트리 생성 API")
@RestController
@RequiredArgsConstructor
public class ChatController {

    private static final String MOCK_PATH_PREFIX = "mocks/chat/tree-";
    private static final String MOCK_PATH_SUFFIX = ".json";

    private static final String MOCK_DESCRIPTION =
        "(선택) mock 시나리오. 지정 시 LLM 을 호출하지 않고 mock 트리를 반환합니다.";

    private final ChatService chatService;
    private final MockResponseLoader mockResponseLoader;

    @Operation(
        summary = "트리 생성 요청",
        description = """
            사용자 메시지를 LLM 에 전달해 계층형 트리를 생성하고 DB 에 저장한 뒤,
            생성된 트리(treeId + nodes)를 반환합니다. 트리를 담을 워크스페이스는 새로 만들어집니다.

            요청 본문: `{ "message": "자료구조 트리 만들어줘", "provider": "OPENAI" }`

            프론트 개발용 mock:
            - `?mock=small` (노드 6개), `?mock=empty`, `?mock=large`, `?mock=max`
            - `mock` 파라미터가 있으면 LLM 을 호출하지 않고 미리 정의된 트리를 반환합니다.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = TreeGenResDTO.class))
    )
    @ApiErrorCodeExample(value = ChatErrorCode.class,
            only = {"EMPTY_MESSAGE", "UNSUPPORTED_PROVIDER", "LLM_CALL_FAILED", "LLM_TIMEOUT",
                    "LLM_RESPONSE_INVALID", "TREE_SAVE_FAILED", "INVALID_MOCK_SCENARIO"})
    @PostMapping("/tree-request")
    public ResponseEntity<ApiResponse<Object>> generateTree(
        @AuthenticationPrincipal Long memberId,
        @RequestBody(required = false) @Valid ChatReqDTO req,
        BindingResult bindingResult,
        @Parameter(
            description = MOCK_DESCRIPTION,
            schema = @Schema(allowableValues = {"empty", "small", "large", "max"})
        )
        @RequestParam(required = false) String mock
    ) throws BindException {
        if (mock != null) {
            return mockResponse(mock, null);
        }
        throwIfInvalid(bindingResult);

        TreeGenResDTO tree = chatService.generateTree(memberId, req);
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
            문서 파일을 업로드해 그 내용으로 트리를 생성합니다. `multipart/form-data` 로 요청하세요.

            - `file`: txt, md, pdf, docx, hwp, hwpx (모든 형식 최대 25MB, 본문 50,000자 이내)
              - 스캔한 이미지 PDF 는 글자를 추출할 수 없어 400 입니다 (OCR 미지원)
              - 암호가 걸린 문서도 400 입니다
            - `message`: (선택) 추가 지시문, 500자 이내. 예) "3단계 깊이로 정리해줘"
            - `provider`: (선택) LLM 제공자 (OPENAI / OLLAMA), 생략 시 OPENAI

            `file` 과 `message` 는 각각 선택이지만 **둘 다 비어 있으면 400** 입니다.
            업로드한 파일은 저장하지 않고 요청 처리 중에만 사용합니다.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = TreeGenResDTO.class))
    )
    @ApiErrorCodeExample(value = ChatErrorCode.class,
            only = {"INPUT_REQUIRED", "UNSUPPORTED_FILE_TYPE", "FILE_TOO_LARGE",
                    "FILE_TEXT_TOO_LONG", "FILE_EMPTY", "FILE_READ_FAILED", "FILE_PARSE_FAILED", "FILE_ENCRYPTED",
                    "UNSUPPORTED_PROVIDER", "LLM_CALL_FAILED", "LLM_TIMEOUT", "LLM_RESPONSE_INVALID",
                    "TREE_SAVE_FAILED", "INVALID_MOCK_SCENARIO"})
    @PostMapping(path = "/tree-request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> generateTreeFromFile(
        @AuthenticationPrincipal Long memberId,
        @Valid @ModelAttribute ChatFileReqDTO req,
        BindingResult bindingResult,
        @Parameter(description = MOCK_DESCRIPTION)
        @RequestParam(required = false) String mock
    ) throws BindException {
        if (mock != null) {
            return mockResponse(mock, null);
        }
        throwIfInvalid(bindingResult);

        TreeGenResDTO tree = chatService.generateTree(memberId, req.provider(), req.message(), req.file());
        return ApiResponse.onSuccess(ChatSuccessCode.TREE_GENERATED, tree);
    }

    /**
     * 기존 워크스페이스에 트리 생성 (JSON).
     *
     * <p>워크스페이스를 새로 만들지 않고 경로의 워크스페이스를 채운다. 요청 형식은 {@code /tree-request} 와 같다.
     */
    @Operation(
        summary = "기존 워크스페이스에 트리 생성 요청",
        description = """
            경로의 워크스페이스에 트리와 노드를 저장합니다. 워크스페이스를 새로 만들지 않고,
            이름도 그대로 둡니다. 응답의 `workspaceId` 는 경로의 값과 같습니다.

            요청 본문은 `/tree-request` 와 같습니다: `{ "message": "자료구조 트리 만들어줘", "provider": "OPENAI" }`

            워크스페이스 상태별 처리:
            - 트리 없음 → 트리를 만들고 노드를 저장
            - 트리는 있고 노드 없음 → 그 트리에 노드를 저장
            - 트리에 노드가 있음 → 409 (`CHAT409-1`)

            프론트 개발용 mock:
            - `?mock=small` (노드 6개), `?mock=empty`, `?mock=large`, `?mock=max`
            - mock 응답의 `workspaceId` 는 경로의 값으로 바꿔 반환합니다.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = TreeGenResDTO.class))
    )
    @ApiErrorCodeExample(value = WorkspaceErrorCode.class,
            only = {"WORKSPACE_NOT_FOUND", "WORKSPACE_ACCESS_DENIED"})
    @ApiErrorCodeExample(value = ChatErrorCode.class,
            only = {"EMPTY_MESSAGE", "WORKSPACE_TREE_NOT_EMPTY", "UNSUPPORTED_PROVIDER", "LLM_CALL_FAILED",
                    "LLM_TIMEOUT", "LLM_RESPONSE_INVALID", "TREE_SAVE_FAILED", "INVALID_MOCK_SCENARIO"})
    @PostMapping("/workspaces/{workspaceId}/tree-request")
    public ResponseEntity<ApiResponse<Object>> generateTreeInWorkspace(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long workspaceId,
        @RequestBody(required = false) @Valid ChatReqDTO req,
        BindingResult bindingResult,
        @Parameter(
            description = MOCK_DESCRIPTION,
            schema = @Schema(allowableValues = {"empty", "small", "large", "max"})
        )
        @RequestParam(required = false) String mock
    ) throws BindException {
        if (mock != null) {
            return mockResponse(mock, workspaceId);
        }
        throwIfInvalid(bindingResult);

        TreeGenResDTO tree = chatService.generateTreeInWorkspace(memberId, workspaceId, req);
        return ApiResponse.onSuccess(ChatSuccessCode.TREE_GENERATED, tree);
    }

    /**
     * 기존 워크스페이스에 트리 생성 (multipart/form-data).
     */
    @Operation(
        summary = "기존 워크스페이스에 트리 생성 요청 (파일 업로드)",
        description = """
            문서 파일을 업로드해 경로의 워크스페이스에 트리를 채웁니다. `multipart/form-data` 로 요청하세요.
            요청 형식과 파일 제한은 `/tree-request` 의 파일 업로드와 같고,
            워크스페이스 상태별 처리는 같은 경로의 JSON 요청과 같습니다.
            """
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            content = @Content(schema = @Schema(implementation = TreeGenResDTO.class))
    )
    @ApiErrorCodeExample(value = WorkspaceErrorCode.class,
            only = {"WORKSPACE_NOT_FOUND", "WORKSPACE_ACCESS_DENIED"})
    @ApiErrorCodeExample(value = ChatErrorCode.class,
            only = {"INPUT_REQUIRED", "WORKSPACE_TREE_NOT_EMPTY", "UNSUPPORTED_FILE_TYPE", "FILE_TOO_LARGE",
                    "FILE_TEXT_TOO_LONG", "FILE_EMPTY", "FILE_READ_FAILED", "FILE_PARSE_FAILED", "FILE_ENCRYPTED",
                    "UNSUPPORTED_PROVIDER", "LLM_CALL_FAILED", "LLM_TIMEOUT", "LLM_RESPONSE_INVALID",
                    "TREE_SAVE_FAILED", "INVALID_MOCK_SCENARIO"})
    @PostMapping(path = "/workspaces/{workspaceId}/tree-request", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Object>> generateTreeInWorkspaceFromFile(
        @AuthenticationPrincipal Long memberId,
        @PathVariable Long workspaceId,
        @Valid @ModelAttribute ChatFileReqDTO req,
        BindingResult bindingResult,
        @Parameter(description = MOCK_DESCRIPTION)
        @RequestParam(required = false) String mock
    ) throws BindException {
        if (mock != null) {
            return mockResponse(mock, workspaceId);
        }
        throwIfInvalid(bindingResult);

        TreeGenResDTO tree = chatService.generateTreeInWorkspace(
            memberId, workspaceId, req.provider(), req.message(), req.file());
        return ApiResponse.onSuccess(ChatSuccessCode.TREE_GENERATED, tree);
    }

    /**
     * 입력 검증 실패를 mock 분기 뒤에서 던진다.
     *
     * <p>BindingResult 를 받으면 Spring 이 검증 실패로 바로 400 을 내지 않으므로,
     * mock 요청은 입력과 무관하게 mock 트리를 받고 실제 요청만 검증 결과를 따른다.
     */
    private void throwIfInvalid(BindingResult bindingResult) throws BindException {
        if (bindingResult.hasErrors()) {
            throw new BindException(bindingResult);
        }
    }

    /**
     * mock 트리 응답.
     *
     * <p>{@code workspaceId} 가 있으면(=워크스페이스 경로) mock 파일의 값 대신 경로의 값을 담아,
     * 실제 응답과 같이 경로와 응답의 워크스페이스가 일치하게 한다.
     */
    private ResponseEntity<ApiResponse<Object>> mockResponse(String scenario, Long workspaceId) {
        validateScenario(scenario);
        JsonNode treeData = mockResponseLoader.load(MOCK_PATH_PREFIX + scenario + MOCK_PATH_SUFFIX);

        if (workspaceId != null && treeData instanceof ObjectNode node) {
            node.put("workspaceId", workspaceId.longValue());
        }
        return ApiResponse.onSuccess(ChatSuccessCode.TREE_GENERATED, treeData);
    }

    /**
     * 경로 탈출 공격 방지 — 소문자/숫자/하이픈만 허용.
     */
    private void validateScenario(String scenario) {
        if (!scenario.matches("[a-z0-9-]+")) {
            throw new ChatException(ChatErrorCode.INVALID_MOCK_SCENARIO);
        }
    }
}

package com.tree.twig_tree.domain.chat.controller;

import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.exception.code.ChatSuccessCode;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.mock.MockResponseLoader;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 채팅 컨트롤러 (현재는 mock 모드).
 *
 * <p>실제 LLM 호출 로직(ChatService)은 그대로 유지되어 있으며, 추후 mock 분기를 제거하면
 * 그대로 사용할 수 있다.
 */
@Tag(name = "Chat (Mock)", description = "트리 생성 요청 mock API")
@RestController
@RequestMapping("/tree-request")
@RequiredArgsConstructor
public class ChatController {

    private static final String MOCK_PATH_PREFIX = "mocks/chat/tree-";
    private static final String MOCK_PATH_SUFFIX = ".json";

    private final MockResponseLoader mockResponseLoader;

    @Operation(
        summary = "트리 생성 요청 (Mock)",
        description = """
            지정된 시나리오에 따라 미리 정의된 트리 데이터를 반환합니다.
            현재 mock 모드이며, `scenario` 파라미터로 응답 데이터를 전환할 수 있습니다.

            - `small` (기본): 노드 5개
            - `empty`: 노드 0개
            - `large`: 노드 50개
            """
    )
    @ApiResponses(value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "트리 조회 성공",
            content = @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(
                        name = "small (기본)",
                        value = """
                        {
                          "isSuccess": true,
                          "code": "TREE_GENERATED",
                          "message": "트리가 성공적으로 생성되었습니다.",
                          "data": {
                            "tree_id": 12,
                            "nodes": [
                              { "node_id": 1, "title": "자료구조", "memo": "데이터를 조직하고 저장하는 방법", "parent_id": null, "order_id": 1 },
                              { "node_id": 2, "title": "알고리즘", "parent_id": null, "order_id": 2 },
                              { "node_id": 3, "title": "배열", "memo": "연속된 메모리 공간", "parent_id": 1, "order_id": 1 },
                              { "node_id": 4, "title": "스택", "memo": "LIFO 자료구조", "parent_id": 1, "order_id": 2 },
                              { "node_id": 5, "title": "정렬", "parent_id": 2, "order_id": 1 }
                            ]
                          }
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "empty (빈 트리)",
                        value = """
                        {
                          "isSuccess": true,
                          "code": "TREE_GENERATED",
                          "message": "트리가 성공적으로 생성되었습니다.",
                          "data": { "tree_id": 99, "nodes": [] }
                        }
                        """
                    ),
                    @ExampleObject(
                        name = "large (대형 트리)",
                        value = """
                        {
                          "isSuccess": true,
                          "code": "TREE_GENERATED",
                          "message": "트리가 성공적으로 생성되었습니다.",
                          "data": {
                            "tree_id": 34,
                            "nodes": [
                              { "node_id": 1, "title": "자료구조", "parent_id": null, "order_id": 1 },
                              "... (총 50개 노드, 깊이 3)"
                            ]
                          }
                        }
                        """
                    )
                }
            )
        )
    })
    @PostMapping
    public ApiResponse<JsonNode> chat(
        @RequestBody(required = false) ChatReqDTO req,
        @Parameter(
            description = "Mock 시나리오 선택",
            schema = @Schema(allowableValues = {"empty", "small", "large"}, defaultValue = "small"),
            example = "small"
        )
        @RequestParam(defaultValue = "small") String scenario
    ) {
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

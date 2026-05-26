package com.tree.twig_tree.domain.chat.controller;

import tools.jackson.databind.JsonNode;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.global.mock.MockResponseLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 채팅 컨트롤러 (현재는 mock 모드).

 * 실제 LLM 호출 로직({@code ChatService})은 그대로 유지되어 있으며, 추후 mock 분기를 제거하면
 * 그대로 사용할 수 있다.

 * 사용 예시:
 *   POST /tree-request                     → tree-small.json  (기본값)
 *   POST /tree-request?scenario=empty      → tree-empty.json  (노드 0개)
 *   POST /tree-request?scenario=small      → tree-small.json  (노드 5개)
 *   POST /tree-request?scenario=large      → tree-large.json  (노드 50개)

 */
@RestController
@RequestMapping("/tree-request")
@RequiredArgsConstructor
public class ChatController {

    private static final String MOCK_PATH_PREFIX = "mocks/chat/tree-";
    private static final String MOCK_PATH_SUFFIX = ".json";

    private final MockResponseLoader mockResponseLoader;

    @PostMapping
    public JsonNode chat(
        @RequestBody(required = false) ChatReqDTO req,  // 잠시 비활성화
        @RequestParam(defaultValue = "small") String scenario
    ) {
        validateScenario(scenario);
        return mockResponseLoader.load(MOCK_PATH_PREFIX + scenario + MOCK_PATH_SUFFIX);
    }

    /**
     * 경로 탈출 공격 방지 — 소문자/숫자/하이픈만 허용.
     */
    private void validateScenario(String scenario) {
        if (!scenario.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException("scenario 값이 올바르지 않습니다: " + scenario);
        }
    }
}

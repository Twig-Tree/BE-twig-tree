package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.prompt.TreePrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LLM 이 생성한 트리를 저장 전에 검증한다.
 *
 * <p>중첩 구조라 참조 무결성과 순환은 표현 자체가 불가능하다. 스키마가 강제하지 못하는
 * 노드 수·깊이·텍스트 길이만 확인하고, 위반하면 {@link ChatErrorCode#LLM_RESPONSE_INVALID} 예외를 던진다.
 */
@Slf4j
@Component
public class TreeValidator {

    public void validate(LlmTreeDTO tree) {
        if (tree == null || tree.root() == null) {
            throw invalid("루트 노드가 없습니다.");
        }

        int nodeCount = 0;
        Deque<NodeAtDepth> stack = new ArrayDeque<>();
        stack.push(new NodeAtDepth(tree.root(), 1));

        while (!stack.isEmpty()) {
            NodeAtDepth current = stack.pop();

            if (++nodeCount > TreePrompt.MAX_NODES) {
                throw invalid("노드 수가 상한(" + TreePrompt.MAX_NODES + ")을 초과했습니다.");
            }
            if (current.depth() > TreePrompt.MAX_DEPTH) {
                throw invalid("트리 깊이가 상한(" + TreePrompt.MAX_DEPTH + "단계)을 초과했습니다.");
            }
            validateFields(current.node());

            for (LlmNode child : current.node().children()) {
                stack.push(new NodeAtDepth(child, current.depth() + 1));
            }
        }
    }

    private void validateFields(LlmNode node) {
        if (node == null) {
            throw invalid("비어 있는 자식 노드가 있습니다.");
        }
        if (node.name() == null || node.name().isBlank()) {
            throw invalid("이름이 비어 있는 노드가 있습니다.");
        }
        // 길이 상한은 nodes 컬럼 정의를 그대로 반영한다(V7). 여기서 걸러내지 않으면
        // 저장 단계에서 DB 제약 위반으로 터져 502 대신 무관한 에러가 노출된다.
        if (node.name().length() > TreePrompt.NAME_MAX_LENGTH) {
            throw invalid("이름이 " + TreePrompt.NAME_MAX_LENGTH + "자를 초과했습니다: length=" + node.name().length());
        }
        if (node.memo() != null && node.memo().length() > TreePrompt.MEMO_MAX_LENGTH) {
            throw invalid("메모가 " + TreePrompt.MEMO_MAX_LENGTH + "자를 초과했습니다: length=" + node.memo().length());
        }
    }

    private ChatException invalid(String reason) {
        log.warn("LLM 트리 검증 실패: {}", reason);
        return new ChatException(ChatErrorCode.LLM_RESPONSE_INVALID);
    }

    /** 루트를 1단계로 센 깊이와 함께 순회 대기 중인 노드. */
    private record NodeAtDepth(LlmNode node, int depth) {
    }
}

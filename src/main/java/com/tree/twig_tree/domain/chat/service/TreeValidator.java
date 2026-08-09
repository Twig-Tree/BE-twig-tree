package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.prompt.TreePrompt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LLM 이 생성한 트리 구조를 저장 전에 검증한다.
 *
 * <p>LLM 출력은 신뢰할 수 없는 외부 입력이므로, 스키마·상한·참조 무결성·순환 여부를 모두 확인한다.
 * 하나라도 위반하면 {@link ChatErrorCode#LLM_RESPONSE_INVALID} 예외를 던진다.
 */
@Slf4j
@Component
public class TreeValidator {

    public void validate(LlmTreeDTO tree) {
        if (tree == null || tree.nodes() == null || tree.nodes().isEmpty()) {
            throw invalid("노드가 비어 있습니다.");
        }

        List<LlmNode> nodes = tree.nodes();
        if (nodes.size() > TreePrompt.MAX_NODES) {
            throw invalid("노드 수가 상한(" + TreePrompt.MAX_NODES + ")을 초과했습니다: " + nodes.size());
        }

        Set<Long> tempIds = new HashSet<>();
        int rootCount = 0;

        for (LlmNode node : nodes) {
            validateFields(node);
            if (!tempIds.add(node.tempId())) {
                throw invalid("tempId 가 중복되었습니다: " + node.tempId());
            }
            if (node.parentTempId() == null) {
                rootCount++;
            }
        }

        // 트리당 루트는 하나뿐이다(uk_nodes_root_per_tree). 여기서 걸러내지 않으면
        // 저장 단계에서 DB 제약 위반으로 터져 원인이 드러나지 않는다.
        if (rootCount == 0) {
            throw invalid("루트 노드(parentTempId=null)가 없습니다.");
        }
        if (rootCount > 1) {
            throw invalid("루트 노드는 하나여야 하는데 " + rootCount + "개입니다.");
        }

        // 같은 부모 아래 형제끼리 orderId 는 겹칠 수 없다(uk_nodes_parent_order_id).
        // 이 인덱스는 parent_id IS NOT NULL 인 행만 대상이므로 루트는 검사에서 제외한다.
        Map<Long, Set<Long>> orderIdsByParent = new HashMap<>();

        for (LlmNode node : nodes) {
            Long parentId = node.parentTempId();
            if (parentId == null) {
                continue;
            }
            if (parentId.equals(node.tempId())) {
                throw invalid("자기 자신을 부모로 지정한 노드가 있습니다: " + node.tempId());
            }
            if (!tempIds.contains(parentId)) {
                throw invalid("존재하지 않는 parentTempId 를 참조합니다: " + parentId);
            }
            if (!orderIdsByParent.computeIfAbsent(parentId, k -> new HashSet<>()).add(node.orderId())) {
                throw invalid("같은 부모 아래에서 orderId 가 중복되었습니다: parentTempId="
                    + parentId + ", orderId=" + node.orderId());
            }
        }

        Map<Long, Long> parentOf = new HashMap<>();
        for (LlmNode node : nodes) {
            parentOf.put(node.tempId(), node.parentTempId());
        }

        validateNoCycle(nodes, parentOf);
        validateDepth(nodes, parentOf);
    }

    private void validateFields(LlmNode node) {
        if (node.tempId() == null) {
            throw invalid("tempId 가 없는 노드가 있습니다.");
        }
        if (node.name() == null || node.name().isBlank()) {
            throw invalid("이름이 비어 있는 노드가 있습니다: tempId=" + node.tempId());
        }
        // 길이 상한은 nodes 컬럼 정의를 그대로 반영한다(V7). 여기서 걸러내지 않으면
        // 저장 단계에서 DB 제약 위반으로 터져 502 대신 무관한 에러가 노출된다.
        if (node.name().length() > TreePrompt.NAME_MAX_LENGTH) {
            throw invalid("이름이 " + TreePrompt.NAME_MAX_LENGTH + "자를 초과했습니다: tempId="
                + node.tempId() + ", length=" + node.name().length());
        }
        if (node.memo() != null && node.memo().length() > TreePrompt.MEMO_MAX_LENGTH) {
            throw invalid("메모가 " + TreePrompt.MEMO_MAX_LENGTH + "자를 초과했습니다: tempId="
                + node.tempId() + ", length=" + node.memo().length());
        }
        if (node.orderId() == null) {
            throw invalid("orderId 가 없는 노드가 있습니다: tempId=" + node.tempId());
        }
    }

    /**
     * 각 노드에서 부모를 따라 루트까지 올라가며 순환을 탐지한다.
     * 방문 횟수가 전체 노드 수를 넘으면 순환으로 판단한다.
     */
    private void validateNoCycle(List<LlmNode> nodes, Map<Long, Long> parentOf) {
        int maxSteps = nodes.size();
        for (LlmNode node : nodes) {
            Long current = node.parentTempId();
            int steps = 0;
            while (current != null) {
                if (steps++ > maxSteps) {
                    throw invalid("순환 참조가 감지되었습니다: tempId=" + node.tempId());
                }
                current = parentOf.get(current);
            }
        }
    }

    /**
     * 루트를 1단계로 세어 각 노드의 깊이가 상한을 넘지 않는지 확인한다.
     *
     * <p>프롬프트만으로는 깊이가 강제되지 않으므로(모델이 지키지 않아도 응답은 유효한 JSON 이다)
     * 여기서 상한을 실제로 막는다. 순환 검증을 통과한 뒤이므로 부모를 따라 올라가면 반드시 끝난다.
     */
    private void validateDepth(List<LlmNode> nodes, Map<Long, Long> parentOf) {
        for (LlmNode node : nodes) {
            int depth = 1;
            for (Long parent = node.parentTempId(); parent != null; parent = parentOf.get(parent)) {
                if (++depth > TreePrompt.MAX_DEPTH) {
                    throw invalid("트리 깊이가 상한(" + TreePrompt.MAX_DEPTH + "단계)을 초과했습니다: tempId="
                        + node.tempId());
                }
            }
        }
    }

    private ChatException invalid(String reason) {
        log.warn("LLM 트리 검증 실패: {}", reason);
        return new ChatException(ChatErrorCode.LLM_RESPONSE_INVALID);
    }
}

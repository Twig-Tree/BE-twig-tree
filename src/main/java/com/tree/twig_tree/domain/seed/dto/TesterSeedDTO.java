package com.tree.twig_tree.domain.seed.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 사용자 테스트용으로 신규 회원에게 미리 넣어 주는 트리 (resources/seed/tester-tree.json).
 *
 * <p>워크스페이스는 폴더 없이 최상위에 만든다. 노드는 LLM 트리와 같은 중첩 구조로 적고,
 * 형제 순서는 {@code children} 의 배열 순서다.
 *
 * <p>{@code tags} 는 트리에 만들 태그 전체 목록이다. 어느 노드에도 붙지 않는 태그도 여기 적으면 만들어진다.
 * 노드의 {@code tags} 에는 이 목록에 있는 이름만 쓸 수 있다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TesterSeedDTO(
    String workspaceName,
    List<String> tags,
    SeedNode root
) {

    public TesterSeedDTO {
        tags = tags == null ? List.of() : tags;
    }

    /** 노드 저장(TreeNodeWriter)과 검증(TreeValidator)에 넘길 LLM 트리 형태로 바꾼다. 태그는 빠진다. */
    public LlmTreeDTO toTree() {
        return new LlmTreeDTO(root.toLlmNode());
    }

    /**
     * 노드를 루트부터 BFS 순서로 나열한다.
     * TreeNodeWriter 가 저장하고 반환하는 순서와 같으므로, 저장된 노드와 위치로 짝지을 수 있다.
     */
    public List<SeedNode> nodesInBfsOrder() {
        List<SeedNode> result = new ArrayList<>();
        Deque<SeedNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            SeedNode node = queue.poll();
            result.add(node);
            queue.addAll(node.children());
        }
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SeedNode(
        String name,
        String memo,
        List<String> tags,
        List<SeedNode> children
    ) {

        public SeedNode {
            tags = tags == null ? List.of() : tags;
            children = children == null ? List.of() : children;
        }

        private LlmNode toLlmNode() {
            return new LlmNode(name, memo, children.stream().map(SeedNode::toLlmNode).toList());
        }
    }
}

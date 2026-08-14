package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 검증이 끝난 LLM 트리 DTO 를 실제 Tree/Node 엔티티로 저장한다.
 *
 * <p>부모가 먼저 persist 되어야 자식의 FK(parent_id)가 잡히므로, 루트부터 BFS 순서로 저장한다.
 * LLM 호출은 이 트랜잭션 밖에서 이뤄지도록 오케스트레이션(ChatService)에서 분리한다.
 */
@Service
@RequiredArgsConstructor
public class GeneratedTreeWriter {

    private final TreeRepository treeRepository;
    private final NodeRepository nodeRepository;

    @Transactional
    public TreeGenResDTO save(LlmTreeDTO llmTree) {
        Tree tree = treeRepository.save(Tree.builder().build());

        List<LlmNode> nodes = llmTree.nodes();

        // parentTempId -> 자식 노드들 (부모가 없으면 루트)
        Map<Long, List<LlmNode>> childrenOf = new HashMap<>();
        List<LlmNode> roots = new ArrayList<>();
        for (LlmNode node : nodes) {
            if (node.parentTempId() == null) {
                roots.add(node);
            } else {
                childrenOf.computeIfAbsent(node.parentTempId(), k -> new ArrayList<>()).add(node);
            }
        }

        // 루트부터 BFS 로 저장하며 tempId -> 저장된 Node 를 매핑
        Map<Long, Node> savedByTempId = new HashMap<>();
        Deque<LlmNode> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            LlmNode dto = queue.poll();
            Node parent = dto.parentTempId() == null ? null : savedByTempId.get(dto.parentTempId());

            Node saved = nodeRepository.save(Node.builder()
                .name(dto.name())
                .memo(dto.memo())
                .orderId(dto.orderId())
                .parent(parent)
                .tree(tree)
                .build());

            savedByTempId.put(dto.tempId(), saved);
            queue.addAll(childrenOf.getOrDefault(dto.tempId(), List.of()));
        }

        // 응답은 입력 노드 순서를 유지한다.
        List<TreeGenResDTO.Node> resNodes = new ArrayList<>();
        for (LlmNode dto : nodes) {
            Node saved = savedByTempId.get(dto.tempId());
            Long parentId = saved.getParent() == null ? null : saved.getParent().getId();
            resNodes.add(TreeGenResDTO.Node.builder()
                .nodeId(saved.getId())
                .name(saved.getName())
                .memo(saved.getMemo())
                .parentId(parentId)
                .orderId(saved.getOrderId())
                .build());
        }

        return TreeGenResDTO.builder()
            .treeId(tree.getId())
            .nodes(resNodes)
            .build();
    }
}

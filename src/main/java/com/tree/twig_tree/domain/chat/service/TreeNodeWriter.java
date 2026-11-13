package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 중첩 구조의 트리 DTO 를 Node 엔티티로 저장한다.
 *
 * <p>부모가 먼저 persist 되어야 자식의 FK(parent_id)가 잡히므로, 루트부터 BFS 순서로 저장한다.
 * 형제 순서(orderId)는 children 배열의 위치(1부터)다. 호출자의 트랜잭션 안에서 부른다.
 *
 * <p>LLM 이 생성한 트리 저장({@link GeneratedTreeWriter})과 테스터 시드 트리 저장이 함께 쓴다.
 */
@Component
@RequiredArgsConstructor
public class TreeNodeWriter {

    private final NodeRepository nodeRepository;

    /** 저장된 엔티티를 저장 순서(BFS)대로 돌려주므로 부모가 항상 자식보다 먼저 나온다. 응답 변환은 호출자가 한다. */
    public List<Node> saveNodes(Tree tree, LlmTreeDTO llmTree) {
        List<Node> savedNodes = new ArrayList<>();
        Deque<PendingNode> queue = new ArrayDeque<>();
        queue.add(new PendingNode(llmTree.root(), null, 1L));

        while (!queue.isEmpty()) {
            PendingNode pending = queue.poll();

            Node saved = nodeRepository.save(Node.builder()
                .name(pending.dto().name())
                .memo(pending.dto().memo())
                .orderId(pending.orderId())
                .parent(pending.parent())
                .tree(tree)
                .build());

            savedNodes.add(saved);

            List<LlmNode> children = pending.dto().children();
            for (int i = 0; i < children.size(); i++) {
                queue.add(new PendingNode(children.get(i), saved, i + 1L));
            }
        }

        return savedNodes;
    }

    /** 저장을 기다리는 노드. 부모는 이미 저장된 엔티티이고, 순서는 형제 배열의 위치(1부터)다. */
    private record PendingNode(LlmNode dto, Node parent, long orderId) {
    }
}

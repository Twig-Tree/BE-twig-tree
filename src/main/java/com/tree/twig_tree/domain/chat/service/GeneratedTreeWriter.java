package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

/**
 * 검증이 끝난 LLM 트리 DTO 를 실제 Tree/Node 엔티티로 저장한다.
 *
 * <p>부모가 먼저 persist 되어야 자식의 FK(parent_id)가 잡히므로, 루트부터 BFS 순서로 저장한다.
 * LLM 호출은 이 트랜잭션 밖에서 이뤄지도록 오케스트레이션(ChatService)에서 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratedTreeWriter {

    private static final int WORKSPACE_NAME_MAX_LENGTH = 30;

    private final TreeRepository treeRepository;
    private final NodeRepository nodeRepository;
    private final WorkspaceRepository workspaceRepository;
    private final MemberService memberService;

    /**
     * LLM 호출이 이미 끝난 뒤(=비용이 이미 발생한 뒤) 호출되므로, 저장 과정에서 나는
     * DB 제약조건 위반(예: 워크스페이스 이름 충돌)이 워크스페이스/트리 도메인 에러코드로 그대로
     * 새 나가지 않도록 채팅 도메인 에러로 감싼다. 프론트가 "트리 생성 요청"의 에러로 해석할 수 있게 하기 위함.
     */
    @Transactional
    public TreeGenResDTO save(Long memberId, LlmTreeDTO llmTree) {
        try {
            return doSave(memberId, llmTree);
        } catch (DataIntegrityViolationException e) {
            log.error("생성된 트리 저장 실패", e);
            throw new ChatException(ChatErrorCode.TREE_SAVE_FAILED);
        }
    }

    private TreeGenResDTO doSave(Long memberId, LlmTreeDTO llmTree) {
        Workspace workspace = createWorkspace(memberId, llmTree);
        Tree tree = treeRepository.save(Tree.builder().workspace(workspace).build());

        // 응답도 저장 순서(BFS)를 따르므로 부모가 항상 자식보다 먼저 나온다.
        List<TreeGenResDTO.Node> resNodes = new ArrayList<>();
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

            resNodes.add(TreeGenResDTO.Node.builder()
                .nodeId(saved.getId())
                .name(saved.getName())
                .memo(saved.getMemo())
                .parentId(pending.parent() == null ? null : pending.parent().getId())
                .orderId(saved.getOrderId())
                .build());

            List<LlmNode> children = pending.dto().children();
            for (int i = 0; i < children.size(); i++) {
                queue.add(new PendingNode(children.get(i), saved, i + 1L));
            }
        }

        return TreeGenResDTO.builder()
            .treeId(tree.getId())
            .workspaceId(workspace.getId())
            .nodes(resNodes)
            .build();
    }

    /**
     * 채팅으로 생성된 트리를 담을 워크스페이스를 자동으로 만든다.
     *
     * <p>최상위 워크스페이스는 이름이 유니크해야 하므로(uk_workspace_root_name),
     * 임시 이름으로 먼저 저장해 id를 발급받은 뒤 그 id로 최종 이름을 확정한다.
     */
    private Workspace createWorkspace(Long memberId, LlmTreeDTO llmTree) {
        Member member = memberService.getById(memberId);

        Workspace workspace = Workspace.builder()
            .name("tmp-" + UUID.randomUUID().toString().substring(0, 8))
            .member(member)
            .build();
        workspaceRepository.save(workspace);

        workspace.updateName(buildWorkspaceName(llmTree, workspace.getId()));
        return workspace;
    }

    private String buildWorkspaceName(LlmTreeDTO llmTree, Long workspaceId) {
        String rootName = llmTree.root().name();

        String suffix = " #" + workspaceId;
        int maxBaseLength = WORKSPACE_NAME_MAX_LENGTH - suffix.length();
        String base = rootName.length() > maxBaseLength ? rootName.substring(0, maxBaseLength) : rootName;
        return base + suffix;
    }

    /** 저장을 기다리는 노드. 부모는 이미 저장된 엔티티이고, 순서는 형제 배열의 위치(1부터)다. */
    private record PendingNode(LlmNode dto, Node parent, long orderId) {
    }
}

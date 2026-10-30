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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final String DEFAULT_WORKSPACE_BASE_NAME = "제목 없음";

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
        String rootName = llmTree.nodes().stream()
            .filter(node -> node.parentTempId() == null)
            .map(LlmNode::name)
            .findFirst()
            .orElse(DEFAULT_WORKSPACE_BASE_NAME);

        String suffix = " #" + workspaceId;
        int maxBaseLength = WORKSPACE_NAME_MAX_LENGTH - suffix.length();
        String base = rootName.length() > maxBaseLength ? rootName.substring(0, maxBaseLength) : rootName;
        return base + suffix;
    }
}

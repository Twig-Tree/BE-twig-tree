package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 검증이 끝난 LLM 트리 DTO 를 실제 Tree/Node 엔티티로 저장한다.
 *
 * <p>노드 저장(BFS)은 {@link TreeNodeWriter} 가 맡는다. LLM 호출은 이 트랜잭션 밖에서 이뤄지도록 오케스트레이션(ChatService)에서 분리한다.
 *
 * <p>저장 대상 워크스페이스는 두 가지다. {@link #save}는 트리를 담을 워크스페이스를 새로 만들고,
 * {@link #saveToWorkspace}는 사용자가 이미 만들어 둔 빈 워크스페이스를 채운다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeneratedTreeWriter {

    private static final int WORKSPACE_NAME_MAX_LENGTH = 30;

    private final TreeRepository treeRepository;
    private final NodeRepository nodeRepository;
    private final TreeNodeWriter treeNodeWriter;
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

        return TreeGenResDTO.builder()
            .treeId(tree.getId())
            .workspaceId(workspace.getId())
            .nodes(treeNodeWriter.saveNodes(tree, llmTree))
            .build();
    }

    /**
     * 이미 있는 워크스페이스에 트리와 노드를 저장한다.
     *
     * <p>워크스페이스 이름은 사용자가 직접 지은 값이므로 바꾸지 않는다.
     *
     * <p>LLM 응답을 기다리는 동안(최대 90초) 같은 워크스페이스에 다른 요청이 들어올 수 있으므로,
     * {@link #validateWritableWorkspace} 로 미리 확인했더라도 저장 시점에 상태를 다시 확인한다.
     */
    @Transactional
    public TreeGenResDTO saveToWorkspace(Long memberId, Long workspaceId, LlmTreeDTO llmTree) {
        try {
            return doSaveToWorkspace(memberId, workspaceId, llmTree);
        } catch (DataIntegrityViolationException e) {
            log.error("생성된 트리 저장 실패 (workspaceId={})", workspaceId, e);
            throw new ChatException(ChatErrorCode.TREE_SAVE_FAILED);
        }
    }

    private TreeGenResDTO doSaveToWorkspace(Long memberId, Long workspaceId, LlmTreeDTO llmTree) {
        // 같은 워크스페이스를 노리는 트리 생성 요청끼리는 락으로 직렬화해, 둘 다 루트를 넣는 일이 없게 한다.
        Workspace workspace = workspaceRepository.findByIdForUpdate(workspaceId)
            .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateWorkspaceOwner(memberId, workspace);

        // 프론트에서 루트 노드 추가가 중간에 실패하면 빈 트리만 남는다. 그 트리를 그대로 채운다.
        Tree tree = treeRepository.findByWorkspace(workspace)
            .orElseGet(() -> treeRepository.save(Tree.builder().workspace(workspace).build()));
        if (nodeRepository.existsByTree_Id(tree.getId())) {
            throw new ChatException(ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);
        }

        List<TreeGenResDTO.Node> resNodes = treeNodeWriter.saveNodes(tree, llmTree);
        workspaceRepository.touchById(workspaceId, Instant.now());

        return TreeGenResDTO.builder()
            .treeId(tree.getId())
            .workspaceId(workspaceId)
            .nodes(resNodes)
            .build();
    }

    /**
     * LLM 호출 전에 대상 워크스페이스를 확인한다.
     *
     * <p>토큰 비용과 응답 대기(최대 90초)가 발생하기 전에 거절할 수 있는 요청을 먼저 걸러낸다.
     * 엔티티 대신 ID 만 읽어, 느린 작업 앞의 이 검사가 커넥션을 오래 잡지 않게 한다.
     * 최종 판단은 저장 시점의 {@link #saveToWorkspace} 가 다시 한다.
     */
    @Transactional(readOnly = true)
    public void validateWritableWorkspace(Long memberId, Long workspaceId) {
        Long ownerId = workspaceRepository.findOwnerMemberIdById(workspaceId)
            .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        if (!ownerId.equals(memberId)) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }

        treeRepository.findIdByWorkspaceId(workspaceId).ifPresent(treeId -> {
            if (nodeRepository.existsByTree_Id(treeId)) {
                throw new ChatException(ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);
            }
        });
    }

    private void validateWorkspaceOwner(Long memberId, Workspace workspace) {
        if (!workspace.getMember().getId().equals(memberId)) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
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
}

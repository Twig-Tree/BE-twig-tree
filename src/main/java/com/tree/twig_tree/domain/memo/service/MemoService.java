package com.tree.twig_tree.domain.memo.service;

import com.tree.twig_tree.domain.memo.converter.MemoConverter;
import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.memo.dto.MemoResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.NodeException;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.exception.TreeException;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoService {

    private final NodeRepository nodeRepository;
    private final TreeRepository treeRepository;
    private final WorkspaceRepository workspaceRepository;

    @Transactional
    public MemoResDTO.GetMemo updateMemo(Long memberId, Long nodeId, MemoReqDTO.UpdateMemo dto) {
        Node node = validateNode(nodeId);
        validateNodeOwner(memberId, node);
        lockTree(node);
        validateVersion(node, dto.version());
        node.updateMemo(dto.content());
        flushOrConflict();
        touchWorkspace(node);

        return MemoConverter.toGetMemo(node);
    }

    public MemoResDTO.GetMemo getMemo(Long memberId, Long nodeId) {
        Node node = validateNode(nodeId);
        validateNodeOwner(memberId, node);
        return MemoConverter.toGetMemo(node);

    }

    @Transactional
    public Void deleteMemo(Long memberId, Long nodeId, Long version) {
        Node node = validateNode(nodeId);
        validateNodeOwner(memberId, node);
        lockTree(node);
        validateVersion(node, version);
        node.updateMemo(null);
        flushOrConflict();
        touchWorkspace(node);

        return null;
    }

    // tree 프록시에서 ID만 꺼내므로 추가 조회가 발생하지 않는다.
    private void touchWorkspace(Node node) {
        workspaceRepository.touchByTreeId(node.getTree().getId(), Instant.now());
    }

    /**
     * 검증 함수
     */

    private Node validateNode(Long nodeId) {
        return nodeRepository.findById(nodeId).orElseThrow(() -> new NodeException(NodeErrorCode.NODE_NOT_FOUND));
    }

    // 노드 소유자 검증
    private void validateNodeOwner(Long memberId, Node node) {
        if (!node.getTree().getWorkspace().getMember().getId().equals(memberId)) {
            throw new NodeException(NodeErrorCode.NODE_ACCESS_DENIED);
        }
    }

    // 노드 이동과 겹치지 않도록 이동과 같은 트리 락을 겁니다.
    // 노드는 락 전에 읽지만, @DynamicUpdate로 memo 컬럼만 UPDATE 하므로 그 사이 이동한 결과를 덮어쓰지 않습니다.
    private void lockTree(Node node) {
        treeRepository.findByIdForUpdate(node.getTree().getId())
                .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
    }

    // 요청이 보고 만든 version이 현재 version과 다르면 다른 곳에서 먼저 수정된 것입니다.
    private void validateVersion(Node node, Long version) {
        if (!node.getVersion().equals(version)) {
            throw new NodeException(NodeErrorCode.VERSION_CONFLICT);
        }
    }

    // UPDATE ... WHERE version = ? 를 즉시 실행합니다.
    // 같은 version으로 동시에 들어온 요청 중 늦은 쪽은 0건이 갱신되어 충돌로 처리되고, 응답에는 올라간 version이 담깁니다.
    private void flushOrConflict() {
        try {
            nodeRepository.flush();
        } catch (OptimisticLockingFailureException e) {
            throw new NodeException(NodeErrorCode.VERSION_CONFLICT);
        }
    }
}

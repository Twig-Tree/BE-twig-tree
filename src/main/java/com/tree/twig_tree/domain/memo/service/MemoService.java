package com.tree.twig_tree.domain.memo.service;

import com.tree.twig_tree.domain.memo.converter.MemoConverter;
import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.memo.dto.MemoResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.NodeException;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemoService {

    private final NodeRepository nodeRepository;

    @Transactional
    public MemoResDTO.GetMemo updateMemo(Long memberId, Long nodeId, MemoReqDTO.UpdateMemo dto) {
        Node node = validateNode(nodeId);
        validateNodeOwner(memberId, node);
        node.updateMemo(dto.content());
        return MemoConverter.toGetMemo(node);
    }

    public MemoResDTO.GetMemo getMemo(Long memberId, Long nodeId) {
        Node node = validateNode(nodeId);
        validateNodeOwner(memberId, node);
        return MemoConverter.toGetMemo(node);

    }

    @Transactional
    public Void deleteMemo(Long memberId, Long nodeId) {
        Node node = validateNode(nodeId);
        validateNodeOwner(memberId, node);
        node.updateMemo(null);
        return null;
    }

    // 검증 함수

    private Node validateNode(Long nodeId) {
        return nodeRepository.findById(nodeId).orElseThrow(() -> new NodeException(NodeErrorCode.NODE_NOT_FOUND));
    }

    // 노드 소유자 검증
    private void validateNodeOwner(Long memberId, Node node) {
        if (!node.getTree().getWorkspace().getMember().getId().equals(memberId)) {
            throw new NodeException(NodeErrorCode.NODE_ACCESS_DENIED);
        }
    }
}

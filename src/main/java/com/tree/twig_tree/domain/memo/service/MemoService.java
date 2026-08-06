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
    public MemoResDTO.GetMemo updateMemo(Long nodeId, MemoReqDTO.UpdateMemo dto) {
        Node node = validateNode(nodeId);
        node.updateMemo(dto.content());

        return MemoConverter.toGetMemo(node);
    }

    public MemoResDTO.GetMemo getMemo(Long nodeId) {
        Node node = validateNode(nodeId);

        return MemoConverter.toGetMemo(node);

    }

    @Transactional
    public Void deleteMemo(Long nodeId) {
        Node node = validateNode(nodeId);
        node.updateMemo(null);

        return null;
    }

    private Node validateNode(Long nodeId) {
        return nodeRepository.findById(nodeId).orElseThrow(() -> new NodeException(NodeErrorCode.NODE_NOT_FOUND));
    }
}

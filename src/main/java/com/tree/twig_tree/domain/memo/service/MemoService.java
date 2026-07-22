package com.tree.twig_tree.domain.memo.service;

import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.memo.dto.MemoResDTO;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MemoService {

    private final NodeRepository nodeRepository;

    public MemoResDTO.GetMemo updateMemo(Long nodeId, MemoReqDTO.UpdateMemo dto) {
        return null;
    }

    public MemoResDTO.GetMemo getMemo(Long nodeId) {
        return null;
    }

    public Void deleteMemo(Long nodeId) {
        return null;
    }
}

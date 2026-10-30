package com.tree.twig_tree.domain.memo.converter;

import com.tree.twig_tree.domain.memo.dto.MemoResDTO;
import com.tree.twig_tree.domain.node.entity.Node;

public class MemoConverter {
    public static MemoResDTO.GetMemo toGetMemo(Node node) {
        return MemoResDTO.GetMemo.builder()
                .title(node.getName())
                .content(node.getMemo())
                .build();
    }
}

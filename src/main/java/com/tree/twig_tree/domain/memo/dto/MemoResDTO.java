package com.tree.twig_tree.domain.memo.dto;

import lombok.Builder;

public class MemoResDTO {
    @Builder
    public record GetMemo(
            String title,
            String content
    ) {}
}

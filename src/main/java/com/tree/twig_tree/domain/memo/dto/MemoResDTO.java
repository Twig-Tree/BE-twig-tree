package com.tree.twig_tree.domain.memo.dto;

public class MemoResDTO {
    public record GetMemo(
            String title,
            String content
    ) {}
}

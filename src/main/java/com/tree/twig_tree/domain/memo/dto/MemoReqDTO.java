package com.tree.twig_tree.domain.memo.dto;

import jakarta.validation.constraints.NotBlank;

public class MemoReqDTO {
    public record UpdateMemo(
            @NotBlank
            String content
    ) {}
}

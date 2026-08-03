package com.tree.twig_tree.domain.memo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class MemoReqDTO {
    public record UpdateMemo(
            @NotBlank
            @Size(max = 500)
            String content
    ) {}
}

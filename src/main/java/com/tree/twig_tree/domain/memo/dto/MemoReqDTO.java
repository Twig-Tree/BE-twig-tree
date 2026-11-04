package com.tree.twig_tree.domain.memo.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class MemoReqDTO {
    public record UpdateMemo(
            @NotBlank
            @Size(max = 500)
            String content,

            @Min(0) @NotNull // 편집을 시작할 때 본 노드의 version
            Long version
    ) {}
}

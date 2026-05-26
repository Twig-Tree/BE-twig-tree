package com.tree.twig_tree.domain.tree.dto;

import lombok.Builder;

public class TreeResDTO {

    @Builder
    public record TreeId(
            Long treeId
    ) {}
}

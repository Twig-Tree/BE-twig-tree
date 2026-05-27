package com.tree.twig_tree.domain.tree.dto;

import lombok.Builder;

public class TreeResDTO {

    @Builder
    public record TreeId(
            Long treeId
    ) {}

    @Builder
    public record GetTree (
            Long treeId,
            String treeName
            // 추가 트리 정보가 추가될 수 있습니다.
    ) {}
}

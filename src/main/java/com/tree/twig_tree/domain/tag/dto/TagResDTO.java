package com.tree.twig_tree.domain.tag.dto;

import lombok.Builder;

public class TagResDTO {

    @Builder
    public record GetTag(
            Long tagId,
            String name
    ) {}
}

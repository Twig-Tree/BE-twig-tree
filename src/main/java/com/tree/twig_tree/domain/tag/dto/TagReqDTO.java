package com.tree.twig_tree.domain.tag.dto;

import jakarta.validation.constraints.NotBlank;
import com.tree.twig_tree.domain.tag.entity.Tag;
import jakarta.validation.constraints.Size;

public class TagReqDTO {
    public record CreateTag (
            @NotBlank
            @Size(max = Tag.NAME_MAX_LENGTH)
            String name
    ){}

    public record EditTagName (
            @NotBlank
            @Size(max = Tag.NAME_MAX_LENGTH)
            String name
    ) {}
}

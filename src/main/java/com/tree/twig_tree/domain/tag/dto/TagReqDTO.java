package com.tree.twig_tree.domain.tag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class TagReqDTO {
    public record CreateTag (
            @NotBlank
            @Size(max = 30)
            String name
    ){}

    public record EditTagName (
            @NotBlank
            @Size(max = 30)
            String name
    ) {}
}

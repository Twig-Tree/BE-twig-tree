package com.tree.twig_tree.domain.node.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class NodeReqDTO {

    public record CreateNode(
            @NotBlank
            String name,

            @Min(1) // root일 수 있으므로 null 가능
            Long parentId,

            @Min(1) @NotNull
            Long orderId
    ) {}

    public record EditNodeName(
            @NotBlank
            String name
    ){}
}

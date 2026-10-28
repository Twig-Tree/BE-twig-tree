package com.tree.twig_tree.domain.node.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class NodeReqDTO {

    public record CreateNode(
            @NotBlank
            @Size(max = 30)
            String name,

            @Min(1) // root일 수 있으므로 null 가능
            Long parentId,

            @Min(1) @NotNull
            Long orderId
    ) {}

    public record EditNodeName(
            @NotBlank
            @Size(max = 30)
            String name
    ){}

    public record MoveNode(
            @Min(1) @NotNull // 루트로는 이동할 수 없으므로 null 불가
            Long parentId,

            @Min(1) @NotNull
            Long position
    ) {}
}

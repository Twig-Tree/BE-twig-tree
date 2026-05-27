package com.tree.twig_tree.domain.node.dto;

import lombok.Builder;

import java.util.List;

public class NodeResDTO {

    @Builder
    public record GetNode(
            Long nodeId,
            Long parentId,
            Long orderId,
            String name,
            String memo
    ) {}

    @Builder
    public record GetTree(
            String treeName,
            List<GetNode> nodes
    ) {}

    // 공용 dto
    @Builder
    public record NodeId(
            Long nodeId
    ) {}
}

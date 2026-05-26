package com.tree.twig_tree.domain.node.dto;

public class NodeReqDTO {

    public record CreateNode(
            String name,
            Long parentId,
            Long orderId
    ) {}

    public record EditNodeName(
            String name
    ){}
}

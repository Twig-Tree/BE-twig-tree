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
            String memo,
            List<Long> tagIds
    ) {}

    @Builder
    public record GetTree(
            List<GetNode> nodes
    ) {}

    // 이동 후 목적지 부모의 자식 노드 목록 (orderId 오름차순)
    @Builder
    public record MoveNode(
            Long parentId,
            List<GetNode> siblings
    ) {}

    // 공용 dto
    @Builder
    public record NodeId(
            Long nodeId
    ) {}
}

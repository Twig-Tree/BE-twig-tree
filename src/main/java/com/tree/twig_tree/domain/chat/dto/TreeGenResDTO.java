package com.tree.twig_tree.domain.chat.dto;

import lombok.Builder;

import java.util.List;

/**
 * 트리 생성 응답 DTO.
 *
 * <p>기존 mock 응답(mocks/chat/tree-*.json)과 동일한 형태를 유지해 프론트 수정이 없도록 한다.
 * 형태: {@code { "treeId": 1, "nodes": [ { "nodeId", "name", "memo", "parentId", "orderId" } ] } }
 */
@Builder
public record TreeGenResDTO(
    Long treeId,
    List<Node> nodes
) {

    @Builder
    public record Node(
        Long nodeId,
        String name,
        String memo,
        Long parentId,
        Long orderId
    ) {}
}

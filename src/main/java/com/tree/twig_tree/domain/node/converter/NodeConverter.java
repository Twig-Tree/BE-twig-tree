package com.tree.twig_tree.domain.node.converter;

import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.dto.NodeResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.tree.entity.Tree;

import java.util.List;

public class NodeConverter {

    // dto -> entity
    public static Node toCreateNode(Tree tree, NodeReqDTO.CreateNode dto) {
        return Node.builder()
                .parentId(dto.parentId())
                .orderId(dto.orderId())
                .name(dto.name())
                .memo(null) // 처음 생성 시 null로 초기화
                .tree(tree)
                .build();
    }

    // entity -> dto
    public static NodeResDTO.GetTree toGetFullTreeNodes(Tree tree, List<Node> fullTreeNodes) {
        return NodeResDTO.GetTree.builder()
                .treeName(tree.getName())
                .nodes(fullTreeNodes.stream()
                        .map(NodeConverter::toGetNode)
                        .toList())
                .build();
    }

    public static List<NodeResDTO.GetNode> toGetSubTreeNodes(List<Node> subTreeNodes) {
        return subTreeNodes.stream()
                .map(NodeConverter::toGetNode)
                .toList();
    }

    // 공용 메서드
    public static NodeResDTO.GetNode toGetNode(Node node) {
        return NodeResDTO.GetNode.builder()
                .nodeId(node.getId())
                .parentId(node.getParentId())
                .orderId(node.getOrderId())
                .name(node.getName())
                .memo(node.getMemo())
                .build();
    }
}

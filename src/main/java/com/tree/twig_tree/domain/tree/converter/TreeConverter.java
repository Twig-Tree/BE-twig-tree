package com.tree.twig_tree.domain.tree.converter;

import com.tree.twig_tree.domain.tree.dto.TreeResDTO;
import com.tree.twig_tree.domain.tree.entity.Tree;

import java.util.List;

public class TreeConverter {
    public static List<TreeResDTO.TreeId> toGetAllTrees(List<Tree> treeList) {
        return treeList.stream()
                .map(TreeConverter::toGetTree)
                .toList();
    }

    public static TreeResDTO.TreeId toGetTree(Tree tree) {
        return TreeResDTO.TreeId.builder()
                .treeId(tree.getId())
                .build();
    }
}

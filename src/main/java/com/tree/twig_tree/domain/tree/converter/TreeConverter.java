package com.tree.twig_tree.domain.tree.converter;

import com.tree.twig_tree.domain.tree.dto.TreeResDTO;
import com.tree.twig_tree.domain.tree.entity.Tree;

import java.util.List;

public class TreeConverter {
    public static List<TreeResDTO.GetTree> toGetAllTrees(List<Tree> treeList) {
        return treeList.stream()
                .map(TreeConverter::toGetTree)
                .toList();
    }

    public static TreeResDTO.GetTree toGetTree(Tree tree) {
        return TreeResDTO.GetTree.builder()
                .treeId(tree.getId())
                .treeName(tree.getName())
                .build();
    }
}

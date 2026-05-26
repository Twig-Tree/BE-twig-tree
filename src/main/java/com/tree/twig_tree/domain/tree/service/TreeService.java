package com.tree.twig_tree.domain.tree.service;

import com.tree.twig_tree.domain.tree.dto.TreeResDTO;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreeService {

    private final TreeRepository treeRepository;

    @Transactional
    public TreeResDTO.TreeId createTree(String name) {
        Tree tree = Tree.builder()
                .name(name)
                .build();

        treeRepository.save(tree);

        return TreeResDTO.TreeId.builder()
                .treeId(tree.getId())
                .build();
    }
}

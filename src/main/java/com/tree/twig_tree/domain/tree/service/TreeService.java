package com.tree.twig_tree.domain.tree.service;

import com.tree.twig_tree.domain.tree.converter.TreeConverter;
import com.tree.twig_tree.domain.tree.dto.TreeResDTO;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.exception.TreeException;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TreeService {

    private final TreeRepository treeRepository;

    /**
     * 모든 트리 조회
     * @return List<Tree>
     */
    public List<TreeResDTO.GetTree> getAllTrees() {
        List<Tree> treeList = treeRepository.findAll();
        return TreeConverter.toGetAllTrees(treeList);
    }

    /**
     * 트리 정보 상세 조회 (노드 정보X)
     * @param treeId
     * @return Tree
     */
    public TreeResDTO.GetTree getTree(Long treeId) {
        Tree tree = treeRepository.findById(treeId).orElseThrow(()->new TreeException(TreeErrorCode.TREE_NOT_FOUND));
        return TreeConverter.toGetTree(tree);
    }

    /**
     * 트리 생성
     * @param name
     * @return treeId
     */
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

package com.tree.twig_tree.domain.node.service;

import com.tree.twig_tree.domain.node.converter.NodeConverter;
import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.dto.NodeResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.NodeException;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
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
public class NodeService {

    private final NodeRepository nodeRepository;
    private final TreeRepository treeRepository;

    /**
     * 노드 생성
     * @param treeId
     * @param dto
     * @return nodeId
     */
    @Transactional
    public NodeResDTO.NodeId createNode(Long treeId, NodeReqDTO.CreateNode dto) {
        Tree tree = treeRepository.findById(treeId)
                .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
        if (dto.parentId() != null) {
            Node parentNode = nodeRepository.findById(dto.parentId()) // findById(null)은 불가능함
                    .orElseThrow(() -> new NodeException(NodeErrorCode.PARENT_NOT_FOUND));

            // 데이터 무결성 체크: 부모 노드의 트리 ID와 현재 요청된 트리 ID가 일치하는가?
            if (!parentNode.getTree().getId().equals(treeId)) {
                throw new NodeException(NodeErrorCode.NODE_NOT_INCLUDED_IN_TREE);
            }
        }

        Node newNode = NodeConverter.toCreateNode(tree, dto);
        Node savedNode = nodeRepository.save(newNode);
        return new NodeResDTO.NodeId(savedNode.getId());
    }

    /**
     * 노드 제목 수정
     * @param treeId
     * @param nodeId
     * @param dto
     * @return nodeId
     */
    @Transactional
    public NodeResDTO.NodeId editNodeName(Long treeId, Long nodeId, NodeReqDTO.EditNodeName dto) {
        Tree tree = treeRepository.findById(treeId)
                .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeException(NodeErrorCode.NODE_NOT_FOUND));

        // 무결성 검증
        if (!node.getTree().getId().equals(tree.getId())) {
            throw new NodeException(NodeErrorCode.NODE_NOT_INCLUDED_IN_TREE);
        }

        node.updateTitle(dto.name());

        return new NodeResDTO.NodeId(node.getId());
    }

    /**
     * 단일 노드 상세 조회
     * @param treeId
     * @param nodeId
     * @return
     */
    public NodeResDTO.GetNode getNode(Long treeId, Long nodeId) {
        treeRepository.findById(treeId)
                .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeException(NodeErrorCode.NODE_NOT_FOUND));

        if (!node.getTree().getId().equals(treeId)) {
            throw new NodeException(NodeErrorCode.NODE_NOT_INCLUDED_IN_TREE);
        }

        return NodeConverter.toGetNode(node);
    }

    /**
     * 트리의 전체 노드 조회
     * @param treeId
     * @return
     */
    public NodeResDTO.GetTree getFullTreeNodes(Long treeId) {
        Tree tree = treeRepository.findById(treeId).orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
        List<Node> fullTreeNodes = nodeRepository.findFullTreeByTreeId(treeId);

        return NodeConverter.toGetFullTreeNodes(tree, fullTreeNodes);

    }

    /**
     * 특정 루트 기준 서브트리 조회
     * @param treeId
     * @param rootId
     * @return
     */
    public List<NodeResDTO.GetNode> getSubTreeNodes(Long treeId, Long rootId) {

        // ------ (추후 구현) 공통 검증 메서드 분리 필요
        treeRepository.findById(treeId)
                .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
        Node node = nodeRepository.findById(rootId)
                .orElseThrow(() -> new NodeException(NodeErrorCode.PARENT_NOT_FOUND));

        if (!node.getTree().getId().equals(treeId)) {
            throw new NodeException(NodeErrorCode.NODE_NOT_INCLUDED_IN_TREE);
        }
        // ------

        List<Node> subTreeNodes = nodeRepository.findSubTreeByRootId(rootId);

        return NodeConverter.toGetSubTreeNodes(subTreeNodes);
    }




}

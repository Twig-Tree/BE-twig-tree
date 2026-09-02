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
     * @return
     */
    @Transactional
    public NodeResDTO.GetNode createNode(Long treeId, NodeReqDTO.CreateNode dto) {
        Tree tree = validateTree(treeId);

        Node parentNode = null;
        if (dto.parentId() != null) {
            parentNode = nodeRepository.findById(dto.parentId()) // findById(null)은 불가능함
                    .orElseThrow(() -> new NodeException(NodeErrorCode.PARENT_NOT_FOUND));

            // 데이터 무결성 체크: 부모 노드의 트리 ID와 현재 요청된 트리 ID가 일치하는가?
            validateNodeInTree(parentNode, treeId);
        }

        Node newNode = NodeConverter.toCreateNode(tree, parentNode, dto);
        Node savedNode = nodeRepository.save(newNode);
        return NodeConverter.toGetNode(savedNode);
    }

    /**
     * 노드 제목 수정
     * @param treeId
     * @param nodeId
     * @param dto
     * @return
     */
    @Transactional
    public NodeResDTO.GetNode editNodeName(Long treeId, Long nodeId, NodeReqDTO.EditNodeName dto) {
        // 검증
        validateTree(treeId);
        Node node = validateNode(nodeId);
        validateNodeInTree(node, treeId);

        node.updateName(dto.name());

        return NodeConverter.toGetNode(node);
    }

    /**
     * 노드 삭제
     * @param nodeId
     */
    @Transactional
    public void deleteNode(Long treeId, Long nodeId) {
        validateTree(treeId);
        Node node = validateNode(nodeId);
        validateNodeInTree(node, treeId);
        nodeRepository.delete(node);
    }

    /**
     * 단일 노드 상세 조회
     * @param treeId
     * @param nodeId
     * @return
     */
    public NodeResDTO.GetNode getNode(Long treeId, Long nodeId) {
        validateTree(treeId);
        Node node = validateNode(nodeId);
        validateNodeInTree(node, treeId);

        return NodeConverter.toGetNode(node);
    }

    /**
     * 트리의 전체 노드 조회
     * @param treeId
     * @return
     */
    public NodeResDTO.GetTree getFullTreeNodes(Long treeId) {
        validateTree(treeId);
        List<Node> fullTreeNodes = nodeRepository.findFullTreeByTreeId(treeId);

        return NodeConverter.toGetFullTreeNodes(fullTreeNodes);

    }

    /**
     * 특정 루트 기준 서브트리 조회
     * @param treeId
     * @param rootId
     * @return
     */
    public List<NodeResDTO.GetNode> getSubTreeNodes(Long treeId, Long rootId) {
        validateTree(treeId);
        Node node = validateNode(rootId);
        validateNodeInTree(node, treeId);

        List<Node> subTreeNodes = nodeRepository.findSubTreeByRootId(rootId);

        return NodeConverter.toGetSubTreeNodes(subTreeNodes);
    }

    /**
     * 공통 검증 메서드
     * : DB 조회 후 찾지 못하면 예외를 발생시킵니다.
     */
    private Tree validateTree(Long treeId) {
        return treeRepository.findById(treeId)
                .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
    }

    private Node validateNode(Long nodeId) {
        return nodeRepository.findById(nodeId)
                .orElseThrow(() -> new NodeException(NodeErrorCode.NODE_NOT_FOUND));
    }

    private void validateNodeInTree(Node node, Long treeId) {
        if (!node.getTree().getId().equals(treeId)) {
            throw new NodeException(NodeErrorCode.NODE_NOT_IN_TREE);
        }
    }


}

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
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NodeService {

    private final NodeRepository nodeRepository;
    private final TreeRepository treeRepository;
    private final WorkspaceRepository workspaceRepository;

    /**
     * 노드 생성
     * @param treeId
     * @param dto
     * @return
     */
    @Transactional
    public NodeResDTO.GetNode createNode(Long memberId, Long treeId, NodeReqDTO.CreateNode dto) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);

        Node parentNode = null;
        if (dto.parentId() != null) {
            parentNode = nodeRepository.findById(dto.parentId()) // findById(null)은 불가능함
                    .orElseThrow(() -> new NodeException(NodeErrorCode.PARENT_NOT_FOUND));

            // 데이터 무결성 체크: 부모 노드의 트리 ID와 현재 요청된 트리 ID가 일치하는가?
            validateNodeInTree(parentNode, treeId);
        }

        Node newNode = NodeConverter.toCreateNode(tree, parentNode, dto);
        Node savedNode = nodeRepository.save(newNode);
        touchWorkspace(treeId);
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
    public NodeResDTO.GetNode editNodeName(Long memberId, Long treeId, Long nodeId, NodeReqDTO.EditNodeName dto) {
        // 검증
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);

        Node node = validateNode(nodeId);
        validateNodeInTree(node, treeId);

        node.updateName(dto.name());
        touchWorkspace(treeId);

        return NodeConverter.toGetNode(node);
    }

    /**
     * 노드 위치 이동
     * : 같은 부모 내 이동과 다른 부모 밑으로의 이동을 모두 처리합니다. 하위 노드는 parent_id로 연결되어 있어 함께 이동합니다.
     *   이동 후 목적지 부모의 형제들은 position 위치에 끼워 1..n으로, 원래 부모의 형제들은 빈자리를 메워 1..n으로 다시 매깁니다.
     * @param treeId
     * @param nodeId
     * @param dto
     * @return 목적지 부모와 원래 부모의 자식 노드 목록
     */
    @Transactional
    public NodeResDTO.MoveNode moveNode(Long memberId, Long treeId, Long nodeId, NodeReqDTO.MoveNode dto) {
        // 동시에 들어온 이동 요청이 같은 형제 목록을 재정렬하지 않도록 트리 단위로 락을 겁니다.
        Tree tree = treeRepository.findByIdForUpdate(treeId)
                .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
        validateTreeOwner(memberId, tree);

        Node node = validateNode(nodeId);
        validateNodeInTree(node, treeId);

        // 이동 대상 노드의 현재 부모가 null이면 그 노드는 root
        if (node.getParent() == null) {
            throw new NodeException(NodeErrorCode.ROOT_NODE_NOT_MOVABLE);
        }

        Node newParent = nodeRepository.findById(dto.parentId())
                .orElseThrow(() -> new NodeException(NodeErrorCode.PARENT_NOT_FOUND));
        validateNodeInTree(newParent, treeId);

        // 목적지 부모가 이동 대상 자신이거나 그 하위 노드이면 순환 구조가 생깁니다.
        if (nodeRepository.isSelfOrAncestor(nodeId, newParent.getId())) {
            throw new NodeException(NodeErrorCode.INVALID_MOVE_TARGET);
        }

        // 변경 전에 목적지·원래 부모의 형제 목록을 불러오고, 이동 대상은 목록에서 뺍니다.
        Node oldParent = node.getParent();
        boolean sameParent = oldParent.getId().equals(newParent.getId());
        List<Node> siblings = new ArrayList<>(nodeRepository.findAllByParentIdOrderByOrderIdAsc(newParent.getId()));
        siblings.remove(node);
        List<Node> previousSiblings = new ArrayList<>();
        if (!sameParent) {
            previousSiblings.addAll(nodeRepository.findAllByParentIdOrderByOrderIdAsc(oldParent.getId()));
            previousSiblings.remove(node);
        }

        // 같은 부모면 1..n, 다른 부모면 1..n+1 (n: 이동 대상을 제외한 목적지 형제 수)
        if (dto.position() > siblings.size() + 1) {
            throw new NodeException(NodeErrorCode.INVALID_POSITION);
        }
        siblings.add(dto.position().intValue() - 1, node);

        // 재정렬 중 order_id가 잠깐 겹쳐도 되도록 유니크 검사를 미룹니다.
        nodeRepository.deferParentOrderIdConstraint();
        node.moveTo(newParent);
        renumber(siblings);
        renumber(previousSiblings);
        nodeRepository.flush();
        // 미뤄둔 검사를 여기서 수행해 제약조건 위반(DUPLICATED_ORDER_ID)을 커밋 전에 드러냅니다.
        nodeRepository.checkParentOrderIdConstraint();
        // 워크스페이스 수정 시각을 갱신
        workspaceRepository.touchByTreeId(treeId, Instant.now());

        return NodeConverter.toMoveNode(newParent.getId(), siblings,
                sameParent ? null : oldParent.getId(), previousSiblings);
    }

    // 형제 노드들의 orderId를 목록 순서대로 1부터 다시 매깁니다. (값이 바뀐 노드만 변경 감지로 UPDATE 됩니다.)
    private void renumber(List<Node> siblings) {
        for (int i = 0; i < siblings.size(); i++) {
            siblings.get(i).changeOrder((long) i + 1);
        }
    }

    /**
     * 노드 삭제
     * @param nodeId
     */
    @Transactional
    public void deleteNode(Long memberId, Long treeId, Long nodeId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);

        Node node = validateNode(nodeId);
        validateNodeInTree(node, treeId);
        nodeRepository.delete(node);
        touchWorkspace(treeId);
    }

    /**
     * 단일 노드 상세 조회
     * @param treeId
     * @param nodeId
     * @return
     */
    public NodeResDTO.GetNode getNode(Long memberId, Long treeId, Long nodeId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);

        Node node = validateNode(nodeId);
        validateNodeInTree(node, treeId);

        return NodeConverter.toGetNode(node);
    }

    /**
     * 트리의 전체 노드 조회
     * @param treeId
     * @return
     */
    public NodeResDTO.GetTree getFullTreeNodes(Long memberId, Long treeId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);
        List<Node> fullTreeNodes = nodeRepository.findFullTreeByTreeId(treeId);

        return NodeConverter.toGetFullTreeNodes(fullTreeNodes);

    }

    /**
     * 특정 루트 기준 서브트리 조회
     * @param treeId
     * @param rootId
     * @return
     */
    public List<NodeResDTO.GetNode> getSubTreeNodes(Long memberId, Long treeId, Long rootId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);

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

    // 트리 소유자 검증
    private void validateTreeOwner(Long memberId, Tree tree) {
        if (!tree.getWorkspace().getMember().getId().equals(memberId)) {
            throw new TreeException(TreeErrorCode.TREE_ACCESS_DENIED);
        }
    }

    // 워크스페이스 updatedAt 갱신
    private void touchWorkspace(Long treeId) {
        workspaceRepository.touchByTreeId(treeId, Instant.now());
    }
}

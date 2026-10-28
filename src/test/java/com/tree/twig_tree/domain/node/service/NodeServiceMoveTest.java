package com.tree.twig_tree.domain.node.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.dto.NodeResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.NodeException;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 노드 위치 이동. 루트·순환 이동은 거절하고, 허용된 이동은 부모와 순서를 바꾼 뒤 목적지 형제 목록을 반환해야 한다.
 */
@ExtendWith(MockitoExtension.class)
class NodeServiceMoveTest {

    private static final Long OWNER_ID = 1L;
    private static final Long TREE_ID = 10L;
    private static final Long OTHER_TREE_ID = 20L;

    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private TreeRepository treeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @InjectMocks
    private NodeService nodeService;

    private final Member owner = Member.builder().id(OWNER_ID).build();
    private final Workspace workspace = Workspace.builder().id(100L).member(owner).build();
    private final Tree tree = Tree.builder().id(TREE_ID).workspace(workspace).build();
    private final Tree otherTree = Tree.builder().id(OTHER_TREE_ID).workspace(workspace).build();

    // root ─┬─ a ── a1
    //       └─ b
    private final Node root = Node.builder().id(1L).name("root").orderId(1L).tree(tree).build();
    private final Node a = Node.builder().id(2L).name("a").orderId(1L).parent(root).tree(tree).build();
    private final Node a1 = Node.builder().id(3L).name("a1").orderId(1L).parent(a).tree(tree).build();
    private final Node b = Node.builder().id(4L).name("b").orderId(2L).parent(root).tree(tree).build();

    @BeforeEach
    void stubTreeLookup() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
    }

    @Test
    @DisplayName("다른 부모 밑으로 이동하면 부모와 순서가 바뀌고 목적지 형제 목록을 반환한다")
    void moveToOtherParent() {
        when(nodeRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(nodeRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(nodeRepository.isSelfOrAncestor(b.getId(), a.getId())).thenReturn(false);
        when(nodeRepository.findAllByParentIdOrderByOrderIdAsc(a.getId())).thenReturn(List.of(a1, b));

        NodeResDTO.MoveNode result = nodeService.moveNode(
                OWNER_ID, TREE_ID, b.getId(), new NodeReqDTO.MoveNode(a.getId(), 2L));

        assertThat(b.getParent()).isSameAs(a);
        assertThat(b.getOrderId()).isEqualTo(2L);
        assertThat(result.parentId()).isEqualTo(a.getId());
        assertThat(result.siblings()).extracting(NodeResDTO.GetNode::nodeId).containsExactly(a1.getId(), b.getId());
        verify(nodeRepository).flush();
        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("루트 노드는 이동할 수 없다")
    void rootNotMovable() {
        when(nodeRepository.findById(root.getId())).thenReturn(Optional.of(root));

        assertNodeError(() -> nodeService.moveNode(
                OWNER_ID, TREE_ID, root.getId(), new NodeReqDTO.MoveNode(a.getId(), 1L)),
                NodeErrorCode.ROOT_NODE_NOT_MOVABLE);
        verify(nodeRepository, never()).flush();
    }

    @Test
    @DisplayName("자기 자신이나 하위 노드 밑으로는 이동할 수 없다")
    void cannotMoveUnderDescendant() {
        when(nodeRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(nodeRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(nodeRepository.isSelfOrAncestor(a.getId(), a1.getId())).thenReturn(true);

        assertNodeError(() -> nodeService.moveNode(
                OWNER_ID, TREE_ID, a.getId(), new NodeReqDTO.MoveNode(a1.getId(), 1L)),
                NodeErrorCode.INVALID_MOVE_TARGET);
        assertThat(a.getParent()).isSameAs(root);
        verify(nodeRepository, never()).flush();
    }

    @Test
    @DisplayName("목적지 부모가 없으면 PARENT_NOT_FOUND")
    void parentNotFound() {
        when(nodeRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(nodeRepository.findById(999L)).thenReturn(Optional.empty());

        assertNodeError(() -> nodeService.moveNode(
                OWNER_ID, TREE_ID, b.getId(), new NodeReqDTO.MoveNode(999L, 1L)),
                NodeErrorCode.PARENT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 트리의 노드 밑으로는 이동할 수 없다")
    void parentInOtherTree() {
        Node foreign = Node.builder().id(50L).name("foreign").orderId(1L).tree(otherTree).build();
        when(nodeRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(nodeRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertNodeError(() -> nodeService.moveNode(
                OWNER_ID, TREE_ID, b.getId(), new NodeReqDTO.MoveNode(foreign.getId(), 1L)),
                NodeErrorCode.NODE_NOT_IN_TREE);
        verify(nodeRepository, never()).isSelfOrAncestor(anyLong(), anyLong());
    }

    private void assertNodeError(ThrowingCallable action, NodeErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(NodeException.class)
                .satisfies(e -> assertThat(((NodeException) e).getErrorCode()).isEqualTo(expected));
    }
}

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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 노드 위치 이동. 루트·순환·범위 밖 이동은 거절하고, 허용된 이동은 목적지·원래 부모의 형제 순서를 1..n으로 다시 매겨야 한다.
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

    // root ─┬─ a ─┬─ a1
    //       │     └─ a2
    //       ├─ b
    //       └─ c
    private final Node root = Node.builder().id(1L).name("root").orderId(1L).tree(tree).build();
    private final Node a = Node.builder().id(2L).name("a").orderId(1L).parent(root).tree(tree).build();
    private final Node b = Node.builder().id(3L).name("b").orderId(2L).parent(root).tree(tree).build();
    private final Node c = Node.builder().id(4L).name("c").orderId(3L).parent(root).tree(tree).build();
    private final Node a1 = Node.builder().id(5L).name("a1").orderId(1L).parent(a).tree(tree).build();
    private final Node a2 = Node.builder().id(6L).name("a2").orderId(2L).parent(a).tree(tree).build();

    @BeforeEach
    void stubTreeLookup() {
        when(treeRepository.findByIdForUpdate(TREE_ID)).thenReturn(Optional.of(tree));
    }

    @Nested
    @DisplayName("같은 부모 내 이동")
    class SameParent {

        @BeforeEach
        void stubSiblings() {
            when(nodeRepository.findById(root.getId())).thenReturn(Optional.of(root));
            when(nodeRepository.findAllByParentIdOrderByOrderIdAsc(root.getId())).thenReturn(List.of(a, b, c));
        }

        @Test
        @DisplayName("뒤에서 앞으로 옮기면 사이의 형제들이 한 칸씩 밀린다")
        void backToFront() {
            when(nodeRepository.findById(c.getId())).thenReturn(Optional.of(c));

            NodeResDTO.MoveNodeRes result = move(c, root, 1L);

            assertOrders(result.siblings(), c, a, b);
            assertThat(result.previousParentId()).isNull();
            assertThat(result.previousSiblings()).isEmpty();
            verify(nodeRepository, never()).findAllByParentIdOrderByOrderIdAsc(a.getId());
        }

        @Test
        @DisplayName("앞에서 뒤로 옮기면 사이의 형제들이 한 칸씩 당겨진다")
        void frontToBack() {
            when(nodeRepository.findById(a.getId())).thenReturn(Optional.of(a));

            NodeResDTO.MoveNodeRes result = move(a, root, 3L);

            assertOrders(result.siblings(), b, c, a);
        }

        @Test
        @DisplayName("같은 부모에서는 n+1 위치로 옮길 수 없다")
        void positionOutOfRange() {
            when(nodeRepository.findById(a.getId())).thenReturn(Optional.of(a));

            assertNodeError(() -> move(a, root, 4L), NodeErrorCode.INVALID_POSITION);
            assertThat(a.getOrderId()).isEqualTo(1L);
            verify(nodeRepository, never()).deferParentOrderIdConstraint();
        }
    }

    @Nested
    @DisplayName("다른 부모 밑으로 이동")
    class OtherParent {

        @BeforeEach
        void stubSiblings() {
            when(nodeRepository.findById(b.getId())).thenReturn(Optional.of(b));
            when(nodeRepository.findById(a.getId())).thenReturn(Optional.of(a));
            when(nodeRepository.isSelfOrAncestor(b.getId(), a.getId())).thenReturn(false);
            when(nodeRepository.findAllByParentIdOrderByOrderIdAsc(a.getId())).thenReturn(List.of(a1, a2));
        }

        @Test
        @DisplayName("목적지 중간에 끼워 넣고, 원래 부모의 빈자리를 메운다")
        void insertInMiddle() {
            when(nodeRepository.findAllByParentIdOrderByOrderIdAsc(root.getId())).thenReturn(List.of(a, b, c));

            NodeResDTO.MoveNodeRes result = move(b, a, 2L);

            assertThat(b.getParent()).isSameAs(a);
            assertThat(result.parentId()).isEqualTo(a.getId());
            assertOrders(result.siblings(), a1, b, a2);
            assertThat(result.previousParentId()).isEqualTo(root.getId());
            assertOrders(result.previousSiblings(), a, c);

            // 유니크 검사를 미룬 뒤 재정렬하고, 반영 후 다시 검사해야 한다.
            InOrder order = inOrder(nodeRepository);
            order.verify(nodeRepository).deferParentOrderIdConstraint();
            order.verify(nodeRepository).flush();
            order.verify(nodeRepository).checkParentOrderIdConstraint();
            verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
        }

        @Test
        @DisplayName("다른 부모에서는 n+1 위치(맨 뒤)까지 허용한다")
        void appendToEnd() {
            when(nodeRepository.findAllByParentIdOrderByOrderIdAsc(root.getId())).thenReturn(List.of(a, b, c));

            NodeResDTO.MoveNodeRes result = move(b, a, 3L);

            assertOrders(result.siblings(), a1, a2, b);
        }

        @Test
        @DisplayName("n+2 위치로는 옮길 수 없다")
        void positionOutOfRange() {
            when(nodeRepository.findAllByParentIdOrderByOrderIdAsc(root.getId())).thenReturn(List.of(a, b, c));

            assertNodeError(() -> move(b, a, 4L), NodeErrorCode.INVALID_POSITION);
            assertThat(b.getParent()).isSameAs(root);
            verify(nodeRepository, never()).deferParentOrderIdConstraint();
        }
    }

    @Test
    @DisplayName("루트 노드는 이동할 수 없다")
    void rootNotMovable() {
        when(nodeRepository.findById(root.getId())).thenReturn(Optional.of(root));

        assertNodeError(() -> move(root, a, 1L), NodeErrorCode.ROOT_NODE_NOT_MOVABLE);
        verify(nodeRepository, never()).deferParentOrderIdConstraint();
    }

    @Test
    @DisplayName("자기 자신이나 하위 노드 밑으로는 이동할 수 없다")
    void cannotMoveUnderDescendant() {
        when(nodeRepository.findById(a.getId())).thenReturn(Optional.of(a));
        when(nodeRepository.findById(a1.getId())).thenReturn(Optional.of(a1));
        when(nodeRepository.isSelfOrAncestor(a.getId(), a1.getId())).thenReturn(true);

        assertNodeError(() -> move(a, a1, 1L), NodeErrorCode.INVALID_MOVE_TARGET);
        assertThat(a.getParent()).isSameAs(root);
        verify(nodeRepository, never()).deferParentOrderIdConstraint();
    }

    @Test
    @DisplayName("목적지 부모가 없으면 PARENT_NOT_FOUND")
    void parentNotFound() {
        when(nodeRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(nodeRepository.findById(999L)).thenReturn(Optional.empty());

        assertNodeError(() -> nodeService.moveNode(
                OWNER_ID, TREE_ID, b.getId(), new NodeReqDTO.MoveNodeReq(999L, 1L)),
                NodeErrorCode.PARENT_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 트리의 노드 밑으로는 이동할 수 없다")
    void parentInOtherTree() {
        Node foreign = Node.builder().id(50L).name("foreign").orderId(1L).tree(otherTree).build();
        when(nodeRepository.findById(b.getId())).thenReturn(Optional.of(b));
        when(nodeRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertNodeError(() -> move(b, foreign, 1L), NodeErrorCode.NODE_NOT_IN_TREE);
        verify(nodeRepository, never()).isSelfOrAncestor(anyLong(), anyLong());
    }

    private NodeResDTO.MoveNodeRes move(Node target, Node newParent, Long position) {
        return nodeService.moveNode(
                OWNER_ID, TREE_ID, target.getId(), new NodeReqDTO.MoveNodeReq(newParent.getId(), position));
    }

    // 형제 목록이 주어진 노드 순서대로 orderId 1..n 을 갖는지 확인
    private void assertOrders(List<NodeResDTO.GetNode> siblings, Node... expected) {
        assertThat(siblings).extracting(NodeResDTO.GetNode::nodeId)
                .containsExactly(Arrays.stream(expected).map(Node::getId).toArray(Long[]::new));
        assertThat(siblings).extracting(NodeResDTO.GetNode::orderId)
                .containsExactly(LongStream.rangeClosed(1, expected.length).boxed().toArray(Long[]::new));
    }

    private void assertNodeError(ThrowingCallable action, NodeErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(NodeException.class)
                .satisfies(e -> assertThat(((NodeException) e).getErrorCode()).isEqualTo(expected));
    }
}

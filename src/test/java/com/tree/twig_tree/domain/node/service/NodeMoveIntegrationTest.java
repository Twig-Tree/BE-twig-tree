package com.tree.twig_tree.domain.node.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.dto.NodeResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 실제 PostgreSQL 에서 노드 이동을 검증한다.
 * 조상 탐색 재귀 CTE, 서브트리 동반 이동, V16 의 DEFERRABLE 제약 덕분에 순서가 겹치는 재정렬이 성공하는지는 DB 없이 확인할 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NodeMoveIntegrationTest {

    @Autowired
    private NodeService nodeService;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private TreeRepository treeRepository;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private MemberRepository memberRepository;

    private Long memberId;
    private Tree tree;

    // root ─┬─ a ── a1 ── a11
    //       └─ b
    private Node root;
    private Node a;
    private Node a1;
    private Node a11;
    private Node b;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.builder()
                .email("move@test.com")
                .provider(Provider.GOOGLE)
                .providerId(UUID.randomUUID().toString())
                .role(Role.ROLE_USER)
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name("ws-" + UUID.randomUUID().toString().substring(0, 8))
                .member(member)
                .build());
        tree = treeRepository.save(Tree.builder().workspace(workspace).build());
        memberId = member.getId();

        root = save("root", null, 1L);
        a = save("a", root, 1L);
        b = save("b", root, 2L);
        a1 = save("a1", a, 1L);
        a11 = save("a11", a1, 1L);
    }

    @Test
    @DisplayName("다른 부모 밑으로 이동하면 하위 노드도 함께 따라온다")
    void moveWithSubtree() {
        NodeResDTO.MoveNode result = move(a1, b, 1L);

        assertThat(result.parentId()).isEqualTo(b.getId());
        assertThat(result.siblings()).extracting(NodeResDTO.GetNode::nodeId).containsExactly(a1.getId());
        assertThat(result.previousParentId()).isEqualTo(a.getId());
        assertThat(result.previousSiblings()).isEmpty();

        List<Long> subtreeIds = nodeRepository.findSubTreeByRootId(b.getId()).stream().map(Node::getId).toList();
        assertThat(subtreeIds).containsExactlyInAnyOrder(b.getId(), a1.getId(), a11.getId());
    }

    @Test
    @DisplayName("같은 부모 내에서 이미 사용 중인 위치로 옮기면 형제와 순서를 맞바꾼다")
    void swapWithinSameParent() {
        NodeResDTO.MoveNode result = move(b, root, 1L);

        assertThat(result.previousParentId()).isNull();
        assertThat(result.siblings())
                .extracting(NodeResDTO.GetNode::nodeId, NodeResDTO.GetNode::orderId)
                .containsExactly(tuple(b.getId(), 1L), tuple(a.getId(), 2L));
        assertStoredOrder(root, b, a);
    }

    @Test
    @DisplayName("다른 부모의 앞쪽에 끼워 넣으면 기존 형제들이 뒤로 밀린다")
    void insertShiftsSiblings() {
        NodeResDTO.MoveNode result = move(a1, root, 1L);

        assertThat(result.siblings())
                .extracting(NodeResDTO.GetNode::nodeId, NodeResDTO.GetNode::orderId)
                .containsExactly(tuple(a1.getId(), 1L), tuple(a.getId(), 2L), tuple(b.getId(), 3L));
        assertStoredOrder(root, a1, a, b);
        assertStoredOrder(a);
    }

    @Test
    @DisplayName("순서에 빈 번호가 있어도 이동 후 목적지·원래 부모 모두 1..n 으로 정리된다")
    void compactsGaps() {
        // root ─┬─ a(1) ─┬─ a1(1) ── a11
        //       │        ├─ a2(5)
        //       │        └─ a3(9)
        //       ├─ b(2)
        //       └─ c(7)
        Node a2 = save("a2", a, 5L);
        Node a3 = save("a3", a, 9L);
        Node c = save("c", root, 7L);

        NodeResDTO.MoveNode result = move(a2, root, 2L);

        assertThat(result.siblings())
                .extracting(NodeResDTO.GetNode::nodeId, NodeResDTO.GetNode::orderId)
                .containsExactly(tuple(a.getId(), 1L), tuple(a2.getId(), 2L), tuple(b.getId(), 3L), tuple(c.getId(), 4L));
        assertThat(result.previousSiblings())
                .extracting(NodeResDTO.GetNode::nodeId, NodeResDTO.GetNode::orderId)
                .containsExactly(tuple(a1.getId(), 1L), tuple(a3.getId(), 2L));
        assertStoredOrder(root, a, a2, b, c);
        assertStoredOrder(a, a1, a3);
    }

    @Test
    @DisplayName("조상 탐색: 자기 자신과 하위 노드는 true, 형제·상위 노드는 false")
    void isSelfOrAncestor() {
        assertThat(nodeRepository.isSelfOrAncestor(a.getId(), a.getId())).isTrue();
        assertThat(nodeRepository.isSelfOrAncestor(a.getId(), a11.getId())).isTrue();
        assertThat(nodeRepository.isSelfOrAncestor(a.getId(), b.getId())).isFalse();
        assertThat(nodeRepository.isSelfOrAncestor(a1.getId(), root.getId())).isFalse();
    }

    private NodeResDTO.MoveNode move(Node target, Node newParent, Long position) {
        return nodeService.moveNode(
                memberId, tree.getId(), target.getId(), new NodeReqDTO.MoveNode(newParent.getId(), position));
    }

    // 커밋된 DB 상태에서 parent 의 자식들이 주어진 순서대로 orderId 1..n 을 갖는지 확인
    private void assertStoredOrder(Node parent, Node... expected) {
        List<Node> children = nodeRepository.findAllByParentIdOrderByOrderIdAsc(parent.getId());
        assertThat(children).extracting(Node::getId)
                .containsExactly(Arrays.stream(expected).map(Node::getId).toArray(Long[]::new));
        for (int i = 0; i < children.size(); i++) {
            assertThat(children.get(i).getOrderId()).isEqualTo(i + 1L);
        }
    }

    private Node save(String name, Node parent, Long orderId) {
        return nodeRepository.save(Node.builder().name(name).parent(parent).orderId(orderId).tree(tree).build());
    }
}

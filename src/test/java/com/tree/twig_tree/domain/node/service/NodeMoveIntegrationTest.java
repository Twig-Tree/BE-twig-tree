package com.tree.twig_tree.domain.node.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.dto.NodeResDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import com.tree.twig_tree.global.apiPayload.util.ConstraintErrorCodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 실제 PostgreSQL 에서 노드 이동을 검증한다.
 * 조상 탐색 재귀 CTE, 서브트리 동반 이동, V14 의 uk_nodes_parent_order_id 제약 매핑은 DB 없이 확인할 수 없다.
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
    private Long treeId;

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
        Tree tree = treeRepository.save(Tree.builder().workspace(workspace).build());
        memberId = member.getId();
        treeId = tree.getId();

        root = save("root", null, 1L, tree);
        a = save("a", root, 1L, tree);
        b = save("b", root, 2L, tree);
        a1 = save("a1", a, 1L, tree);
        a11 = save("a11", a1, 1L, tree);
    }

    @Test
    @DisplayName("다른 부모 밑으로 이동하면 하위 노드도 함께 따라온다")
    void moveWithSubtree() {
        NodeResDTO.MoveNode result = nodeService.moveNode(
                memberId, treeId, a1.getId(), new NodeReqDTO.MoveNode(b.getId(), 1L));

        assertThat(result.parentId()).isEqualTo(b.getId());
        assertThat(result.siblings()).extracting(NodeResDTO.GetNode::nodeId).containsExactly(a1.getId());

        List<Long> subtreeIds = nodeRepository.findSubTreeByRootId(b.getId()).stream().map(Node::getId).toList();
        assertThat(subtreeIds).containsExactlyInAnyOrder(b.getId(), a1.getId(), a11.getId());
    }

    @Test
    @DisplayName("같은 부모 내에서 비어 있는 position 으로 이동하면 순서만 바뀐다")
    void moveWithinSameParent() {
        NodeResDTO.MoveNode result = nodeService.moveNode(
                memberId, treeId, a.getId(), new NodeReqDTO.MoveNode(root.getId(), 3L));

        assertThat(result.siblings())
                .extracting(NodeResDTO.GetNode::nodeId, NodeResDTO.GetNode::orderId)
                .containsExactly(
                        tuple(b.getId(), 2L),
                        tuple(a.getId(), 3L));
    }

    @Test
    @DisplayName("조상 탐색: 자기 자신과 하위 노드는 true, 형제·상위 노드는 false")
    void isSelfOrAncestor() {
        assertThat(nodeRepository.isSelfOrAncestor(a.getId(), a.getId())).isTrue();
        assertThat(nodeRepository.isSelfOrAncestor(a.getId(), a11.getId())).isTrue();
        assertThat(nodeRepository.isSelfOrAncestor(a.getId(), b.getId())).isFalse();
        assertThat(nodeRepository.isSelfOrAncestor(a1.getId(), root.getId())).isFalse();
    }

    @Test
    @DisplayName("이미 사용 중인 position 으로 이동하면 DUPLICATED_ORDER_ID 로 매핑된다")
    void duplicatedPosition() {
        assertThatThrownBy(() -> nodeService.moveNode(
                memberId, treeId, a1.getId(), new NodeReqDTO.MoveNode(root.getId(), 2L)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(ConstraintErrorCodeMapper.getErrorCode(e))
                        .isEqualTo(NodeErrorCode.DUPLICATED_ORDER_ID));

        // 롤백되어 원래 위치에 그대로 있어야 한다.
        Node reloaded = nodeRepository.findAllByParentIdOrderByOrderIdAsc(a.getId()).get(0);
        assertThat(reloaded.getId()).isEqualTo(a1.getId());
    }

    private Node save(String name, Node parent, Long orderId, Tree tree) {
        return nodeRepository.save(Node.builder().name(name).parent(parent).orderId(orderId).tree(tree).build());
    }
}

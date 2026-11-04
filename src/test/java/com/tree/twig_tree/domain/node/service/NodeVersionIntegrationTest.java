package com.tree.twig_tree.domain.node.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.memo.dto.MemoResDTO;
import com.tree.twig_tree.domain.memo.service.MemoService;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 PostgreSQL 에서 노드 version 충돌 검증을 확인한다.
 * 동시에 들어온 요청 중 하나만 UPDATE ... WHERE version = ? 를 통과하는지, native CTE 가 version 을 매핑하는지,
 * 이동과 이름 수정이 겹칠 때 서로의 컬럼을 덮어쓰지 않는지는 DB 없이 확인할 수 없다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class NodeVersionIntegrationTest {

    @Autowired
    private NodeService nodeService;
    @Autowired
    private MemoService memoService;
    @Autowired
    private NodeRepository nodeRepository;
    @Autowired
    private TreeRepository treeRepository;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long memberId;
    private Tree tree;

    // root ─┬─ a
    //       └─ b
    private Node root;
    private Node a;
    private Node b;

    @BeforeEach
    void setUp() {
        Member member = memberRepository.save(Member.builder()
                .email("version@test.com")
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
    }

    @Test
    @DisplayName("현재 version으로 이름을 수정하면 저장되고 응답에 올라간 version이 담긴다")
    void editNameWithCurrentVersion() {
        NodeResDTO.GetNode result = editName(a, "새 이름", 0L);

        assertThat(result.version()).isEqualTo(1L);
        assertThat(stored(a).getName()).isEqualTo("새 이름");
        assertThat(stored(a).getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("오래된 version으로 이름을 수정하면 VERSION_CONFLICT 이고 저장되지 않는다")
    void editNameWithStaleVersion() {
        editName(a, "다른 탭", 0L);

        assertVersionConflict(() -> editName(a, "오래된 화면", 0L));
        assertThat(stored(a).getName()).isEqualTo("다른 탭");
        assertThat(stored(a).getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("메모 수정·삭제도 version을 확인하고, 이름과 같은 version을 공유한다")
    void memoSharesVersionWithName() {
        MemoResDTO.GetMemo updated = memoService.updateMemo(memberId, a.getId(), new MemoReqDTO.UpdateMemo("메모", 0L));
        assertThat(updated.version()).isEqualTo(1L);

        // 메모 수정으로 version이 올라갔으므로 그 전 화면 기준의 이름 수정은 막힌다.
        assertVersionConflict(() -> editName(a, "오래된 화면", 0L));
        assertVersionConflict(() -> memoService.deleteMemo(memberId, a.getId(), 0L));
        assertThat(stored(a).getMemo()).isEqualTo("메모");

        memoService.deleteMemo(memberId, a.getId(), 1L);
        assertThat(stored(a).getMemo()).isNull();
        assertThat(stored(a).getVersion()).isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 version으로 동시에 수정하면 하나만 성공하고 나머지는 VERSION_CONFLICT 이다")
    void concurrentEditsOnlyOneWins() throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            String name = "탭" + i;
            futures.add(executor.submit(() -> {
                start.await();
                return editName(a, name, 0L);
            }));
        }
        start.countDown();

        int success = 0;
        int conflict = 0;
        for (Future<?> future : futures) {
            try {
                future.get();
                success++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(NodeException.class);
                assertThat(((NodeException) e.getCause()).getErrorCode()).isEqualTo(NodeErrorCode.VERSION_CONFLICT);
                conflict++;
            }
        }
        executor.shutdown();

        assertThat(success).isEqualTo(1);
        assertThat(conflict).isEqualTo(threads - 1);
        assertThat(stored(a).getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("노드 이동·형제 재정렬은 version을 올리지 않는다")
    void moveDoesNotBumpVersion() {
        nodeService.moveNode(memberId, tree.getId(), b.getId(), new NodeReqDTO.MoveNodeReq(root.getId(), 1L));

        assertThat(stored(b).getOrderId()).isEqualTo(1L);
        assertThat(stored(a).getOrderId()).isEqualTo(2L);
        assertThat(stored(a).getVersion()).isZero();
        assertThat(stored(b).getVersion()).isZero();

        // 다른 탭에서 형제를 옮겨도 이름 수정은 충돌하지 않는다.
        editName(a, "새 이름", 0L);
    }

    @Test
    @DisplayName("트리 전체 조회 응답에 각 노드의 version이 담긴다")
    void fullTreeIncludesVersion() {
        editName(a, "새 이름", 0L);

        NodeResDTO.GetTree result = nodeService.getFullTreeNodes(memberId, tree.getId());

        assertThat(result.nodes())
                .filteredOn(node -> node.nodeId().equals(a.getId()))
                .extracting(NodeResDTO.GetNode::version)
                .containsExactly(1L);
        assertThat(result.nodes())
                .filteredOn(node -> node.nodeId().equals(b.getId()))
                .extracting(NodeResDTO.GetNode::version)
                .containsExactly(0L);
    }

    @Test
    @DisplayName("메모 수정이 이동 전에 노드를 읽고 이동이 먼저 커밋해도 이동 결과를 덮어쓰지 않는다")
    void memoUpdateAfterConcurrentMoveKeepsMove() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 메모 수정은 트리 락 전에 노드를 읽으므로, 이동 전 상태(parent=root, orderId=1)를 먼저 읽어 둔 상황을 만든다.
            nodeRepository.findById(a.getId()).orElseThrow();
            runInOtherTransaction(() -> nodeService.moveNode(
                    memberId, tree.getId(), a.getId(), new NodeReqDTO.MoveNodeReq(b.getId(), 1L)));
            memoService.updateMemo(memberId, a.getId(), new MemoReqDTO.UpdateMemo("메모", 0L));
        });

        assertThat(stored(a).getMemo()).isEqualTo("메모");
        assertThat(stored(a).getParent().getId()).isEqualTo(b.getId());
        assertThat(stored(a).getOrderId()).isEqualTo(1L);
        assertThat(stored(b).getOrderId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("이동이 진행 중이면 이름 수정은 이동이 커밋될 때까지 기다린 뒤 최신 상태 위에 저장된다")
    void editNameWaitsForMoveInProgress() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<NodeResDTO.GetNode> edit;
        try {
            edit = new TransactionTemplate(transactionManager).execute(status -> {
                // 이동 트랜잭션이 트리 락을 잡고 형제 목록을 읽은 상태
                treeRepository.findByIdForUpdate(tree.getId()).orElseThrow();
                nodeRepository.findAllByParentIdOrderByOrderIdAsc(root.getId());

                // 이 사이에 들어온 이름 수정은 트리 락에서 기다려야 한다.
                Future<NodeResDTO.GetNode> pending = executor.submit(() -> editName(a, "새 이름", 0L));
                assertThatThrownBy(() -> pending.get(500, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

                // b를 맨 앞으로 옮기면 a의 orderId가 1 -> 2로 바뀌어 a도 UPDATE 된다.
                nodeService.moveNode(memberId, tree.getId(), b.getId(), new NodeReqDTO.MoveNodeReq(root.getId(), 1L));
                return pending;
            });
            // 이동이 커밋되면 이름 수정이 이어서 진행된다.
            assertThat(edit.get(5, TimeUnit.SECONDS).version()).isEqualTo(1L);
        } finally {
            executor.shutdown();
        }

        assertThat(stored(a).getName()).isEqualTo("새 이름");
        assertThat(stored(a).getOrderId()).isEqualTo(2L);
        assertThat(stored(b).getOrderId()).isEqualTo(1L);
    }

    // 다른 스레드(= 다른 트랜잭션)에서 실행하고 커밋될 때까지 기다린다.
    private void runInOtherTransaction(Runnable action) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(action).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(e);
        } finally {
            executor.shutdown();
        }
    }

    private NodeResDTO.GetNode editName(Node target, String name, Long version) {
        return nodeService.editNodeName(memberId, tree.getId(), target.getId(), new NodeReqDTO.EditNodeName(name, version));
    }

    private void assertVersionConflict(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(NodeException.class)
                .satisfies(e -> assertThat(((NodeException) e).getErrorCode())
                        .isEqualTo(NodeErrorCode.VERSION_CONFLICT));
    }

    // 커밋된 DB 상태를 다시 읽어온다.
    private Node stored(Node node) {
        return nodeRepository.findById(node.getId()).orElseThrow();
    }

    private Node save(String name, Node parent, Long orderId) {
        return nodeRepository.save(Node.builder().name(name).parent(parent).orderId(orderId).tree(tree).build());
    }
}

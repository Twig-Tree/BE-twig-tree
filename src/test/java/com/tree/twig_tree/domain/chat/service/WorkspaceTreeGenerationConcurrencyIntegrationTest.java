package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.node.service.NodeService;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.util.ConstraintErrorCodeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.assertj.core.api.Assertions.fail;

/**
 * 실제 PostgreSQL 에서, 빈 트리를 두고 AI 트리 생성과 수동 루트 추가가 겹칠 때를 확인한다.
 *
 * <p>두 경로가 같은 워크스페이스 락({@code WorkspaceRepository.findByIdForUpdate})으로 직렬화되는지는
 * 호출 순서를 보는 mock 테스트로는 끝까지 확인할 수 없다. 락이 없으면 커밋되지 않은 루트 삽입을
 * 트리 생성 쪽 노드 존재 검사가 보지 못해, 그 요청이 의도한 {@code CHAT409-1} 대신
 * {@code uk_nodes_root_per_tree} 위반으로 끝난다.
 *
 * <p>테스트는 세 가지다. 앞의 두 개는 락을 먼저 잡는 쪽을 고정해 <b>양쪽 순서를 모두</b> 확인하고,
 * 선행 트랜잭션이 락을 쥔 동안 후행 요청이 실제로 <b>대기하는지</b>까지 본다. 마지막 하나는 두 실제
 * 경로를 같은 순간에 띄우는 비결정적 경합으로, 어떤 순서로 끝나든 불변식이 지켜지는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WorkspaceTreeGenerationConcurrencyIntegrationTest {

    /** 후행 요청이 락을 기다리는지 볼 때 쓰는 대기 시간. 이 안에 끝나면 직렬화가 안 된 것이다. */
    private static final long BLOCKED_PROBE_SECONDS = 1;
    private static final long FINISH_TIMEOUT_SECONDS = 30;

    @Autowired
    private GeneratedTreeWriter generatedTreeWriter;
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
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private Long memberId;
    private Long workspaceId;
    private Long treeId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);

        Member member = memberRepository.save(Member.builder()
                .email("concurrency@test.com")
                .provider(Provider.GOOGLE)
                .providerId(UUID.randomUUID().toString())
                .role(Role.ROLE_USER)
                .build());
        Workspace workspace = workspaceRepository.save(Workspace.builder()
                .name("ws-" + UUID.randomUUID().toString().substring(0, 8))
                .member(member)
                .build());
        // 프론트가 루트 노드 추가에 실패해 빈 트리만 남은 상태. 이 상태가 두 경로의 경합 대상이다.
        Tree tree = treeRepository.save(Tree.builder().workspace(workspace).build());

        memberId = member.getId();
        workspaceId = workspace.getId();
        treeId = tree.getId();
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("수동 루트 추가가 락을 먼저 잡으면, 트리 생성은 그 커밋을 기다린 뒤 CHAT409-1 로 끝난다")
    void generationWaitsForManualRootThenRejects() throws Exception {
        LockHolder leader = holdWorkspaceLock(() -> saveRoot("수동 루트"));

        Future<?> generation = executor.submit(() -> generatedTreeWriter.saveToWorkspace(memberId, workspaceId, llmTree()));
        assertStillBlocked(generation, "트리 생성");

        leader.release();

        Throwable error = errorOf(generation);
        assertThat(error)
                .as("트리 생성이 제약 위반으로 끝나면 프론트가 409 분기를 할 수 없다")
                .isInstanceOf(ChatException.class);
        assertThat(((ChatException) error).getErrorCode()).isEqualTo(ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);

        assertSingleRoot("수동 루트");
    }

    @Test
    @DisplayName("트리 생성이 락을 먼저 잡으면, 수동 루트 추가는 그 커밋을 기다린 뒤 루트 제약에 걸린다")
    void manualRootWaitsForGenerationThenHitsRootConstraint() throws Exception {
        // 선행은 트리 생성 경로가 잡는 것과 같은 락을 잡고 루트를 넣은 뒤 커밋을 붙잡아 두는 역할만 한다.
        LockHolder leader = holdWorkspaceLock(() -> saveRoot("AI 루트"));

        Future<?> manualRoot = executor.submit(
                () -> nodeService.createNode(memberId, treeId, new NodeReqDTO.CreateNode("수동 루트", null, 1L)));
        assertStillBlocked(manualRoot, "수동 루트 추가");

        leader.release();

        Throwable error = errorOf(manualRoot);
        assertThat(error).isInstanceOf(DataIntegrityViolationException.class);
        BaseErrorCode mapped = ConstraintErrorCodeMapper.getErrorCode((DataIntegrityViolationException) error);
        assertThat(mapped).isEqualTo(NodeErrorCode.ONE_ROOT_PER_TREE);

        assertSingleRoot("AI 루트");
    }

    @Test
    @DisplayName("두 요청이 같은 순간에 들어와도 하나만 성공하고, 트리 생성은 제약 위반으로 끝나지 않는다")
    void generationAndManualRootDoNotRaceIntoConstraintViolation() throws Exception {
        CountDownLatch start = new CountDownLatch(1);

        Future<?> generation = executor.submit(() -> {
            start.await();
            return generatedTreeWriter.saveToWorkspace(memberId, workspaceId, llmTree());
        });
        Future<?> manualRoot = executor.submit(() -> {
            start.await();
            return nodeService.createNode(memberId, treeId, new NodeReqDTO.CreateNode("수동 루트", null, 1L));
        });
        start.countDown();

        Throwable generationError = errorOf(generation);
        Throwable manualRootError = errorOf(manualRoot);

        // 어느 순서로 끝났는지는 실행마다 다르다. 순서와 무관하게 참이어야 하는 것만 단정한다.
        assertThat(generationError == null ^ manualRootError == null)
                .as("둘 중 정확히 하나만 성공해야 한다 (생성 오류=%s, 수동 추가 오류=%s)", generationError, manualRootError)
                .isTrue();

        if (generationError != null) {
            assertThat(generationError).isInstanceOf(ChatException.class);
            assertThat(((ChatException) generationError).getErrorCode())
                    .isEqualTo(ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);
        } else {
            assertThat(manualRootError).isInstanceOf(DataIntegrityViolationException.class);
        }

        List<Node> stored = nodeRepository.findFullTreeByTreeId(treeId);
        assertThat(stored.stream().filter(n -> n.getParent() == null)).hasSize(1);
        assertThat(treeRepository.findIdByWorkspaceId(workspaceId)).contains(treeId);
    }

    // --- 도우미 ---

    /**
     * 워크스페이스 락을 잡고 {@code work} 를 수행한 뒤, {@link LockHolder#release()} 가 불릴 때까지
     * 커밋하지 않고 트랜잭션을 열어 둔다. 후행 요청이 실제로 대기하는지 보기 위한 장치다.
     */
    private LockHolder holdWorkspaceLock(Runnable work) throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> future = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            workspaceRepository.findByIdForUpdate(workspaceId)
                    .orElseThrow(() -> new IllegalStateException("테스트 워크스페이스가 없습니다"));
            work.run();
            holding.countDown();
            try {
                // 커밋을 붙잡아 둔다. 이 동안 후행 요청은 같은 행의 락을 기다려야 한다.
                if (!release.await(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("release 신호가 오지 않았습니다");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            return null;
        }));

        assertThat(holding.await(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("선행 트랜잭션이 락을 잡지 못했습니다")
                .isTrue();
        return new LockHolder(release, future);
    }

    /** 선행 트랜잭션이 락을 쥔 동안 후행 요청이 끝나지 않는지 확인한다. */
    private void assertStillBlocked(Future<?> follower, String label) {
        assertThatThrownBy(() -> follower.get(BLOCKED_PROBE_SECONDS, TimeUnit.SECONDS))
                .as("%s 가 선행 트랜잭션의 커밋을 기다리지 않고 끝났다 = 직렬화가 안 된 것", label)
                .isInstanceOf(TimeoutException.class);
    }

    private void assertSingleRoot(String expectedRootName) {
        List<Node> stored = nodeRepository.findFullTreeByTreeId(treeId);
        assertThat(stored.stream().filter(n -> n.getParent() == null))
                .singleElement()
                .extracting(Node::getName)
                .isEqualTo(expectedRootName);
    }

    private Node saveRoot(String name) {
        Tree tree = treeRepository.findById(treeId).orElseThrow();
        return nodeRepository.save(Node.builder().name(name).orderId(1L).tree(tree).build());
    }

    /** 성공하면 null, 실패하면 원인 예외를 돌려준다. */
    private Throwable errorOf(Future<?> future) throws Exception {
        try {
            future.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return null;
        } catch (ExecutionException e) {
            return e.getCause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail("동시 실행이 중단되었습니다", e);
        }
    }

    private LlmTreeDTO llmTree() {
        return new LlmTreeDTO(new LlmNode("AI 루트", "설명", List.of(
                new LlmNode("AI 자식", null, List.of())
        )));
    }

    /** 락을 쥔 선행 트랜잭션. release() 를 부르면 커밋된다. */
    private record LockHolder(CountDownLatch releaseLatch, Future<?> future) {

        void release() throws Exception {
            releaseLatch.countDown();
            future.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }
}

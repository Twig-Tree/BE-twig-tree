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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * 실제 PostgreSQL 에서, 빈 트리를 두고 AI 트리 생성과 수동 루트 추가가 동시에 들어올 때를 확인한다.
 *
 * <p>두 경로가 같은 워크스페이스 락({@code WorkspaceRepository.findByIdForUpdate})으로 직렬화되는지는
 * 호출 순서를 보는 mock 테스트로는 끝까지 확인할 수 없다. 락이 없으면 커밋되지 않은 루트 삽입을
 * 트리 생성 쪽 노드 존재 검사가 보지 못해, 그 요청이 의도한 {@code CHAT409-1} 대신
 * {@code uk_nodes_root_per_tree} 위반으로 끝난다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WorkspaceTreeGenerationConcurrencyIntegrationTest {

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

    private Long memberId;
    private Long workspaceId;
    private Long treeId;

    @BeforeEach
    void setUp() {
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

    @Test
    @DisplayName("트리 생성과 수동 루트 추가가 동시에 들어오면 하나만 성공하고, 트리 생성은 제약 위반 대신 CHAT409-1 로 끝난다")
    void generationAndManualRootDoNotRaceIntoConstraintViolation() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
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
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        // 락을 누가 먼저 잡느냐에 따라 결과 조합은 둘 중 하나다.
        //  (1) 트리 생성이 먼저 → 생성 성공, 수동 추가는 uk_nodes_root_per_tree 위반(ONE_ROOT_PER_TREE)
        //  (2) 수동 추가가 먼저 → 수동 성공, 트리 생성은 WORKSPACE_TREE_NOT_EMPTY(CHAT409-1)
        // 어느 쪽이든 성공은 정확히 하나이고, 루트도 하나만 남는다.
        assertThat(generationError == null ^ manualRootError == null)
                .as("둘 중 정확히 하나만 성공해야 한다 (생성 오류=%s, 수동 추가 오류=%s)", generationError, manualRootError)
                .isTrue();

        if (generationError != null) {
            // 트리 생성이 진 경우: 반드시 도메인 에러코드로 거절돼야 하고, 제약 위반이 새 나가면 안 된다.
            assertThat(generationError)
                    .as("트리 생성이 제약 위반으로 끝나면 프론트가 409 분기를 할 수 없다")
                    .isInstanceOf(ChatException.class);
            assertThat(((ChatException) generationError).getErrorCode())
                    .isEqualTo(ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);
        } else {
            // 수동 추가가 진 경우: 루트 유니크 인덱스에 걸리고, 그 제약은 ONE_ROOT_PER_TREE 로 매핑된다.
            assertThat(manualRootError).isInstanceOf(DataIntegrityViolationException.class);
            BaseErrorCode mapped =
                    ConstraintErrorCodeMapper.getErrorCode((DataIntegrityViolationException) manualRootError);
            assertThat(mapped).isEqualTo(NodeErrorCode.ONE_ROOT_PER_TREE);
        }

        List<Node> stored = nodeRepository.findFullTreeByTreeId(treeId);
        assertThat(stored.stream().filter(n -> n.getParent() == null)).hasSize(1);
        // 트리가 하나 더 생기지도 않아야 한다 (워크스페이스당 트리는 하나).
        assertThat(treeRepository.findIdByWorkspaceId(workspaceId)).contains(treeId);
    }

    /** 성공하면 null, 실패하면 원인 예외를 돌려준다. */
    private Throwable errorOf(Future<?> future) throws Exception {
        try {
            future.get(30, TimeUnit.SECONDS);
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
}

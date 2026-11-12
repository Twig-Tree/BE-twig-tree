package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 PostgreSQL 에서, 워크스페이스 이름 수정과 위치 이동이 겹칠 때를 확인한다.
 *
 * <p>두 요청은 잠금 없이 같은 행을 수정한다. {@code @DynamicUpdate} 가 없으면 Hibernate 가 매핑된
 * 모든 컬럼을 다시 쓰므로, 상대의 커밋 전에 읽어 둔 값이 그 커밋을 덮어쓴다. 이 PR 에서 이동 API 가
 * {@code folder_id} 를 바꾸기 시작하면서 생긴 위험이다.
 *
 * <p>워크스페이스에는 계층이 없어 순환이 생길 수 없으므로 폴더 쪽처럼 회원 락까지 잡지는 않는다.
 * 같은 컬럼을 동시에 고치는 경우는 그대로 마지막 쓰기가 이긴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class WorkspaceMoveConcurrencyIntegrationTest {

    private static final long FINISH_TIMEOUT_SECONDS = 30;

    @Autowired
    private WorkspaceService workspaceService;
    @Autowired
    private WorkspaceRepository workspaceRepository;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private Long memberId;
    private Long workspaceId;
    private Long folderId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(1);

        Member member = memberRepository.save(Member.builder()
                .email("workspace-move-concurrency-" + UUID.randomUUID() + "@test.com")
                .provider(Provider.GOOGLE)
                .providerId(UUID.randomUUID().toString())
                .role(Role.ROLE_USER)
                .build());
        memberId = member.getId();

        Folder folder = folderRepository.save(Folder.builder().name("폴더").member(member).build());
        folderId = folder.getId();
        // 루트에 있는 워크스페이스. 이름 수정과 폴더 이동이 겹치는 대상이다.
        workspaceId = workspaceRepository.save(
                Workspace.builder().name("원래 이름").member(member).build()).getId();
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("이동 전에 읽어 둔 이름 수정이 커밋돼도 이동 결과를 덮어쓰지 않는다")
    void renameDoesNotOverwriteMove() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch moved = new CountDownLatch(1);

        // 이동이 커밋되기 전의 워크스페이스(폴더 없음)를 읽어 두고, 이동 후에 이름만 바꾼다.
        Future<?> rename = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Workspace stale = workspaceRepository.findById(workspaceId).orElseThrow();
            loaded.countDown();
            awaitOrFail(moved, "이동 완료 신호");
            stale.updateName("바뀐 이름");
            return null;
        }));

        assertThat(loaded.await(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("이름 수정 트랜잭션이 이동 전 상태를 읽지 못했습니다")
                .isTrue();

        workspaceService.moveWorkspace(memberId, workspaceId, new WorkspaceReqDTO.MoveWorkspace(folderId));
        moved.countDown();
        rename.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Workspace stored = workspaceRepository.findById(workspaceId).orElseThrow();
        assertThat(stored.getName()).isEqualTo("바뀐 이름");
        assertThat(stored.getFolder())
                .as("@DynamicUpdate 가 없으면 이름 수정이 오래된 folder_id 까지 다시 써서 이동이 사라진다")
                .isNotNull();
        assertThat(stored.getFolder().getId()).isEqualTo(folderId);
    }

    @Test
    @DisplayName("이름 수정 전에 읽어 둔 이동이 커밋돼도 이름 수정 결과를 덮어쓰지 않는다")
    void moveDoesNotOverwriteRename() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch renamed = new CountDownLatch(1);

        // 반대 방향. 이름 수정 전의 워크스페이스를 읽어 두고, 이름이 바뀐 뒤에 폴더만 옮긴다.
        Future<?> move = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Workspace stale = workspaceRepository.findById(workspaceId).orElseThrow();
            loaded.countDown();
            awaitOrFail(renamed, "이름 수정 완료 신호");
            stale.moveTo(folderRepository.findById(folderId).orElseThrow());
            return null;
        }));

        assertThat(loaded.await(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("이동 트랜잭션이 이름 수정 전 상태를 읽지 못했습니다")
                .isTrue();

        workspaceService.updateWorkspace(memberId, workspaceId, "바뀐 이름");
        renamed.countDown();
        move.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Workspace stored = workspaceRepository.findById(workspaceId).orElseThrow();
        assertThat(stored.getFolder()).isNotNull();
        assertThat(stored.getFolder().getId()).isEqualTo(folderId);
        assertThat(stored.getName())
                .as("@DynamicUpdate 가 없으면 이동이 오래된 name 까지 다시 써서 이름 수정이 사라진다")
                .isEqualTo("바뀐 이름");
    }

    private static void awaitOrFail(CountDownLatch latch, String label) {
        try {
            if (!latch.await(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(label + " 이 오지 않았습니다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

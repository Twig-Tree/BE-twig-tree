package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.TestcontainersConfiguration;
import com.tree.twig_tree.domain.folder.dto.FolderProjection;
import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.entity.enums.Provider;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.domain.member.repository.MemberRepository;
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
 * 실제 PostgreSQL 에서, 같은 회원의 폴더 이동 요청이 겹칠 때를 확인한다.
 *
 * <p>순환 검사({@code isSelfOrAncestor})와 부모 변경 사이가 벌어지면 READ COMMITTED 에서
 * A→B 와 B→A 가 모두 검사를 통과한 뒤 서로 다른 행을 갱신해 순환이 저장된다. 그러면
 * {@code findAncestorPathRaw} 의 {@code UNION ALL} 재귀 CTE 가 끝나지 않는다. 폴더 계층에는
 * 노드의 트리처럼 대표 행이 없어 회원 행을 공통 잠금 대상으로 쓴다.
 *
 * <p>이름 수정은 이 락을 잡지 않으므로, 이동과 겹쳤을 때 오래된 값으로 서로를 덮어쓰지 않는지
 * ({@code @DynamicUpdate}) 함께 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FolderMoveConcurrencyIntegrationTest {

    /** 후행 요청이 락을 기다리는지 볼 때 쓰는 대기 시간. 이 안에 끝나면 직렬화가 안 된 것이다. */
    private static final long BLOCKED_PROBE_SECONDS = 1;
    private static final long FINISH_TIMEOUT_SECONDS = 30;

    @Autowired
    private FolderService folderService;
    @Autowired
    private FolderRepository folderRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private ExecutorService executor;
    private Member member;
    private Long memberId;
    private Long aId;
    private Long bId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);

        member = memberRepository.save(Member.builder()
                .email("folder-move-concurrency-" + UUID.randomUUID() + "@test.com")
                .provider(Provider.GOOGLE)
                .providerId(UUID.randomUUID().toString())
                .role(Role.ROLE_USER)
                .build());
        memberId = member.getId();

        // 루트에 나란히 있는 두 폴더. 서로를 부모로 삼으려는 경합 대상이다.
        aId = save("A", null).getId();
        bId = save("B", null).getId();
    }

    @AfterEach
    void tearDown() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("회원 락을 쥔 트랜잭션이 있으면 폴더 이동은 그 커밋을 기다린다")
    void moveWaitsForMemberLock() throws Exception {
        LockHolder leader = holdMemberLock(() -> { });

        Future<?> move = executor.submit(() -> move(aId, bId));
        assertStillBlocked(move, "폴더 이동");

        leader.release();

        assertThat(errorOf(move)).isNull();
        assertThat(parentIdOf(aId)).isEqualTo(bId);
    }

    @Test
    @DisplayName("A→B 가 커밋되기를 기다린 B→A 는 다시 검사해 INVALID_MOVE_TARGET 으로 끝난다")
    void secondMoveRecheckesAfterLock() throws Exception {
        // 선행은 이동 경로가 잡는 것과 같은 락을 잡고 A 를 B 밑으로 옮긴 뒤 커밋을 붙잡아 둔다.
        LockHolder leader = holdMemberLock(() -> {
            Folder a = folderRepository.findById(aId).orElseThrow();
            a.moveTo(folderRepository.findById(bId).orElseThrow());
        });

        Future<?> reverse = executor.submit(() -> move(bId, aId));
        assertStillBlocked(reverse, "반대 방향 이동");

        leader.release();

        Throwable error = errorOf(reverse);
        assertThat(error)
                .as("락을 얻은 뒤 순환을 다시 검사하지 않으면 A↔B 순환이 저장된다")
                .isInstanceOf(FolderException.class);
        assertThat(((FolderException) error).getErrorCode()).isEqualTo(FolderErrorCode.INVALID_MOVE_TARGET);

        assertNoCycle();
    }

    @Test
    @DisplayName("서로를 부모로 삼는 두 이동이 같은 순간에 들어와도 하나만 성공하고 순환이 저장되지 않는다")
    void oppositeMovesDoNotRaceIntoCycle() throws Exception {
        CountDownLatch start = new CountDownLatch(1);

        Future<?> forward = executor.submit(() -> {
            start.await();
            return move(aId, bId);
        });
        Future<?> backward = executor.submit(() -> {
            start.await();
            return move(bId, aId);
        });
        start.countDown();

        Throwable forwardError = errorOf(forward);
        Throwable backwardError = errorOf(backward);

        // 어느 쪽이 락을 먼저 잡는지는 실행마다 다르다. 순서와 무관하게 참이어야 하는 것만 단정한다.
        assertThat(forwardError == null ^ backwardError == null)
                .as("둘 중 정확히 하나만 성공해야 한다 (A→B 오류=%s, B→A 오류=%s)", forwardError, backwardError)
                .isTrue();

        Throwable loserError = forwardError != null ? forwardError : backwardError;
        assertThat(loserError).isInstanceOf(FolderException.class);
        assertThat(((FolderException) loserError).getErrorCode()).isEqualTo(FolderErrorCode.INVALID_MOVE_TARGET);

        assertNoCycle();
    }

    @Test
    @DisplayName("이동 전에 읽어 둔 이름 수정이 커밋돼도 이동 결과를 덮어쓰지 않는다")
    void renameDoesNotOverwriteMove() throws Exception {
        CountDownLatch loaded = new CountDownLatch(1);
        CountDownLatch moved = new CountDownLatch(1);

        // 이름 수정은 회원 락을 잡지 않으므로 이동과 겹칠 수 있다.
        // 이 트랜잭션은 이동 전의 A(부모 없음)를 읽어 두고, 이동이 커밋된 뒤에 이름만 바꾼다.
        Future<?> rename = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            Folder stale = folderRepository.findById(aId).orElseThrow();
            loaded.countDown();
            awaitOrFail(moved, "이동 완료 신호");
            stale.updateName("바뀐 A");
            return null;
        }));

        assertThat(loaded.await(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS))
                .as("이름 수정 트랜잭션이 이동 전 상태를 읽지 못했습니다")
                .isTrue();

        move(aId, bId);
        moved.countDown();
        rename.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        Folder stored = folderRepository.findById(aId).orElseThrow();
        assertThat(stored.getName()).isEqualTo("바뀐 A");
        assertThat(stored.getParent())
                .as("@DynamicUpdate 가 없으면 이름 수정이 오래된 folder_parent_id 까지 다시 써서 이동이 사라진다")
                .isNotNull();
        assertThat(stored.getParent().getId()).isEqualTo(bId);
    }

    // --- 도우미 ---

    /**
     * 회원 락을 잡고 {@code work} 를 수행한 뒤, {@link LockHolder#release()} 가 불릴 때까지
     * 커밋하지 않고 트랜잭션을 열어 둔다. 후행 요청이 실제로 대기하는지 보기 위한 장치다.
     */
    private LockHolder holdMemberLock(Runnable work) throws Exception {
        CountDownLatch holding = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> future = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
            memberRepository.findByIdForUpdate(memberId)
                    .orElseThrow(() -> new IllegalStateException("테스트 회원이 없습니다"));
            work.run();
            holding.countDown();
            // 커밋을 붙잡아 둔다. 이 동안 후행 요청은 같은 회원 행의 락을 기다려야 한다.
            awaitOrFail(release, "release 신호");
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

    /**
     * 조상 경로 조회가 끝나는지로 순환이 저장되지 않았음을 확인한다.
     * 순환이 저장되면 UNION ALL 재귀 CTE 가 멈추지 않으므로, 이 조회 자체가 검증이다.
     */
    private void assertNoCycle() {
        List<String> aPath = ancestorNames(aId);
        List<String> bPath = ancestorNames(bId);
        assertThat(aPath).doesNotHaveDuplicates().endsWith("A");
        assertThat(bPath).doesNotHaveDuplicates().endsWith("B");
        // 한쪽만 다른 쪽 밑에 있어야 한다. 양쪽 모두 상대를 조상으로 두면 순환이다.
        assertThat(aPath.contains("B") && bPath.contains("A")).isFalse();
    }

    private List<String> ancestorNames(Long folderId) {
        return folderRepository.findAncestorPathRaw(folderId).stream()
                .map(FolderProjection.FolderAncestorProjection::getName)
                .toList();
    }

    private Object move(Long folderId, Long newParentId) {
        return folderService.moveFolder(memberId, folderId, new FolderReqDTO.MoveFolder(newParentId));
    }

    private Long parentIdOf(Long folderId) {
        Folder parent = folderRepository.findById(folderId).orElseThrow().getParent();
        return parent == null ? null : parent.getId();
    }

    private Folder save(String name, Folder parent) {
        return folderRepository.save(Folder.builder().name(name).parent(parent).member(member).build());
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

    /** 락을 쥔 선행 트랜잭션. release() 를 부르면 커밋된다. */
    private record LockHolder(CountDownLatch releaseLatch, Future<?> future) {

        void release() throws Exception {
            releaseLatch.countDown();
            future.get(FINISH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }
}

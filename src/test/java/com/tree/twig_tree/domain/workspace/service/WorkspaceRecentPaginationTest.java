package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceCursor;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 전체 워크스페이스 최신순 커서 페이지네이션.
 * size + 1 개를 조회해 hasNext 를 판단하고, 마지막 항목으로 nextCursor 를 만들어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceRecentPaginationTest {

    private static final Long MEMBER_ID = 1L;
    private static final Instant BASE_TIME = Instant.parse("2026-10-03T08:00:00.123456Z");

    @Mock
    private FolderRepository folderRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private TreeRepository treeRepository;
    @Mock
    private MemberService memberService;
    @InjectMocks
    private WorkspaceService workspaceService;

    /**
     * 최신순으로 정렬된 워크스페이스 count 개 (id 가 클수록 최신).
     * updatedAt 은 BaseEntity 의 private 필드라 리플렉션으로 채운다.
     */
    private static List<Workspace> workspacesNewestFirst(int count) {
        Member member = Member.builder().id(MEMBER_ID).build();
        return LongStream.iterate(count, id -> id - 1).limit(count)
                .mapToObj(id -> {
                    Workspace workspace = Workspace.builder().id(id).name("ws-" + id).member(member).build();
                    ReflectionTestUtils.setField(workspace, "updatedAt", BASE_TIME.plusSeconds(id));
                    return workspace;
                })
                .toList();
    }

    @Nested
    @DisplayName("첫 페이지 (cursor 없음)")
    class FirstPage {

        @Test
        @DisplayName("size 보다 많이 조회되면 size 개만 반환하고 hasNext = true, nextCursor 는 마지막 항목")
        void hasNext() {
            // size = 2 → 3 개 조회됨 → 다음 페이지 있음
            when(workspaceRepository.findRecentFirstPage(eq(MEMBER_ID), any(Pageable.class)))
                    .thenReturn(workspacesNewestFirst(3));

            WorkspaceResDTO.GetWorkspaceSlice result = workspaceService.getAllWorkspaces(MEMBER_ID, null, 2);

            assertThat(result.workspaces()).extracting(WorkspaceResDTO.GetWorkspace::workspaceId)
                    .containsExactly(3L, 2L);
            assertThat(result.hasNext()).isTrue();
            assertThat(WorkspaceCursor.decode(result.nextCursor()))
                    .isEqualTo(new WorkspaceCursor(BASE_TIME.plusSeconds(2), 2L));
        }

        @Test
        @DisplayName("size 개 이하로 조회되면 hasNext = false, nextCursor = null")
        void lastPage() {
            when(workspaceRepository.findRecentFirstPage(eq(MEMBER_ID), any(Pageable.class)))
                    .thenReturn(workspacesNewestFirst(2));

            WorkspaceResDTO.GetWorkspaceSlice result = workspaceService.getAllWorkspaces(MEMBER_ID, null, 2);

            assertThat(result.workspaces()).hasSize(2);
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("워크스페이스가 없으면 빈 목록, hasNext = false")
        void empty() {
            when(workspaceRepository.findRecentFirstPage(eq(MEMBER_ID), any(Pageable.class)))
                    .thenReturn(List.of());

            WorkspaceResDTO.GetWorkspaceSlice result = workspaceService.getAllWorkspaces(MEMBER_ID, null, 20);

            assertThat(result.workspaces()).isEmpty();
            assertThat(result.hasNext()).isFalse();
            assertThat(result.nextCursor()).isNull();
        }

        @Test
        @DisplayName("빈 문자열 cursor 는 첫 페이지로 취급한다")
        void blankCursor() {
            when(workspaceRepository.findRecentFirstPage(eq(MEMBER_ID), any(Pageable.class)))
                    .thenReturn(List.of());

            workspaceService.getAllWorkspaces(MEMBER_ID, " ", 20);

            verify(workspaceRepository, never()).findRecentAfterCursor(anyLong(), any(), anyLong(), any());
        }
    }

    @Nested
    @DisplayName("다음 페이지 (cursor 있음)")
    class NextPage {

        @Test
        @DisplayName("커서를 디코딩한 (updatedAt, id) 로 다음 페이지를 조회한다")
        void passesDecodedCursor() {
            Instant cursorTime = BASE_TIME.plusSeconds(10);
            String cursor = new WorkspaceCursor(cursorTime, 10L).encode();
            when(workspaceRepository.findRecentAfterCursor(eq(MEMBER_ID), eq(cursorTime), eq(10L), any(Pageable.class)))
                    .thenReturn(workspacesNewestFirst(1));

            WorkspaceResDTO.GetWorkspaceSlice result = workspaceService.getAllWorkspaces(MEMBER_ID, cursor, 20);

            assertThat(result.workspaces()).hasSize(1);
            assertThat(result.hasNext()).isFalse();
            verify(workspaceRepository, never()).findRecentFirstPage(anyLong(), any());
        }

        @Test
        @DisplayName("잘못된 커서는 INVALID_CURSOR 이고 DB 를 조회하지 않는다")
        void invalidCursor() {
            assertThatThrownBy(() -> workspaceService.getAllWorkspaces(MEMBER_ID, "%%%", 20))
                    .isInstanceOf(WorkspaceException.class)
                    .satisfies(e -> assertThat(((WorkspaceException) e).getErrorCode())
                            .isEqualTo(WorkspaceErrorCode.INVALID_CURSOR));

            verify(workspaceRepository, never()).findRecentAfterCursor(anyLong(), any(), anyLong(), any());
        }
    }
}

package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 워크스페이스 소유권 검증. 다른 회원의 워크스페이스를 건드리면 WORKSPACE_ACCESS_DENIED 여야 한다.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long WORKSPACE_ID = 100L;

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

    private Workspace workspaceOwnedByOwner() {
        Member owner = Member.builder().id(OWNER_ID).build();
        return Workspace.builder().id(WORKSPACE_ID).name("내 워크스페이스").member(owner).build();
    }

    @Nested
    @DisplayName("다른 회원 소유 워크스페이스에 접근하면 WORKSPACE_ACCESS_DENIED")
    class OtherMembersWorkspace {

        @BeforeEach
        void stubWorkspaceLookup() {
            when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceOwnedByOwner()));
        }

        @Test
        @DisplayName("조회")
        void getWorkspace() {
            assertAccessDenied(() -> workspaceService.getWorkspace(OTHER_ID, WORKSPACE_ID));
            verify(treeRepository, never()).findByWorkspace(any());
        }

        @Test
        @DisplayName("이름 수정")
        void updateWorkspace() {
            assertAccessDenied(() -> workspaceService.updateWorkspace(OTHER_ID, WORKSPACE_ID, "바뀐 이름"));
        }

        @Test
        @DisplayName("삭제")
        void deleteWorkspace() {
            assertAccessDenied(() -> workspaceService.deleteWorkspace(OTHER_ID, WORKSPACE_ID));
            verify(workspaceRepository, never()).delete(any());
        }
    }

    @Test
    @DisplayName("본인 소유 워크스페이스는 소유권 검증을 통과한다")
    void ownWorkspacePasses() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceOwnedByOwner()));
        when(treeRepository.findByWorkspace(any())).thenReturn(Optional.empty());

        assertThatCode(() -> workspaceService.getWorkspace(OWNER_ID, WORKSPACE_ID)).doesNotThrowAnyException();
    }

    private void assertAccessDenied(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(WorkspaceException.class)
                .satisfies(e -> assertThat(((WorkspaceException) e).getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
    }
}

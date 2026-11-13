package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 워크스페이스 위치 이동 검증.
 *
 * <p>이름 중복(부분 UNIQUE 인덱스)과 updatedAt 갱신은 실제 DB 가 필요해
 * {@code WorkspaceMoveIntegrationTest} 에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceServiceMoveTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long WORKSPACE_ID = 100L;
    private static final Long FOLDER_ID = 200L;

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

    private Workspace workspace(Long memberId) {
        Member member = Member.builder().id(memberId).build();
        return Workspace.builder().id(WORKSPACE_ID).name("내 워크스페이스").member(member).build();
    }

    private Folder folder(Long memberId) {
        Member member = Member.builder().id(memberId).build();
        return Folder.builder().id(FOLDER_ID).name("목적지 폴더").member(member).build();
    }

    private WorkspaceReqDTO.MoveWorkspace moveTo(Long folderId) {
        return new WorkspaceReqDTO.MoveWorkspace(folderId);
    }

    @Test
    @DisplayName("folderId 가 null 이면 폴더에 속하지 않는 최상위로 이동한다")
    void moveToRoot() {
        Workspace target = workspace(OWNER_ID);
        target.moveTo(folder(OWNER_ID));
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(target));
        when(treeRepository.findByWorkspace(target)).thenReturn(Optional.empty());

        var result = workspaceService.moveWorkspace(OWNER_ID, WORKSPACE_ID, moveTo(null));

        assertThat(result.folderId()).isNull();
        assertThat(target.getFolder()).isNull();
        verify(folderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("폴더 아래로 이동하면 폴더가 바뀐다")
    void moveUnderFolder() {
        Workspace target = workspace(OWNER_ID);
        Folder destination = folder(OWNER_ID);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(target));
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(destination));
        when(treeRepository.findByWorkspace(target)).thenReturn(Optional.empty());

        var result = workspaceService.moveWorkspace(OWNER_ID, WORKSPACE_ID, moveTo(FOLDER_ID));

        assertThat(result.folderId()).isEqualTo(FOLDER_ID);
        assertThat(target.getFolder()).isSameAs(destination);
    }

    @Test
    @DisplayName("목적지 폴더가 없으면 FOLDER_NOT_FOUND")
    void folderNotFound() {
        Workspace target = workspace(OWNER_ID);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(target));
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.moveWorkspace(OWNER_ID, WORKSPACE_ID, moveTo(FOLDER_ID)))
                .isInstanceOf(FolderException.class)
                .satisfies(e -> assertThat(((FolderException) e).getErrorCode())
                        .isEqualTo(FolderErrorCode.FOLDER_NOT_FOUND));
        verify(workspaceRepository, never()).flush();
    }

    @Test
    @DisplayName("다른 회원 소유 폴더로 옮기면 FOLDER_ACCESS_DENIED")
    void movingIntoOtherMembersFolder() {
        Workspace target = workspace(OWNER_ID);
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(target));
        when(folderRepository.findById(FOLDER_ID)).thenReturn(Optional.of(folder(OTHER_ID)));

        assertThatThrownBy(() -> workspaceService.moveWorkspace(OWNER_ID, WORKSPACE_ID, moveTo(FOLDER_ID)))
                .isInstanceOf(FolderException.class)
                .satisfies(e -> assertThat(((FolderException) e).getErrorCode())
                        .isEqualTo(FolderErrorCode.FOLDER_ACCESS_DENIED));
        assertThat(target.getFolder()).isNull();
        verify(workspaceRepository, never()).flush();
    }

    @Test
    @DisplayName("다른 회원 소유 워크스페이스를 옮기면 WORKSPACE_ACCESS_DENIED")
    void movingOtherMembersWorkspace() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace(OWNER_ID)));

        assertThatThrownBy(() -> workspaceService.moveWorkspace(OTHER_ID, WORKSPACE_ID, moveTo(null)))
                .isInstanceOf(WorkspaceException.class)
                .satisfies(e -> assertThat(((WorkspaceException) e).getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
        verify(workspaceRepository, never()).flush();
    }
}

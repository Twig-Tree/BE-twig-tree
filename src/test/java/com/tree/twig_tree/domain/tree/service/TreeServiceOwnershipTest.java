package com.tree.twig_tree.domain.tree.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 트리 소유권 검증. 트리는 워크스페이스 소유자 기준으로 판단하며, 다른 회원이면 WORKSPACE_ACCESS_DENIED 여야 한다.
 */
@ExtendWith(MockitoExtension.class)
class TreeServiceOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long WORKSPACE_ID = 100L;
    private static final Long TREE_ID = 10L;

    @Mock
    private TreeRepository treeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @InjectMocks
    private TreeService treeService;

    private Workspace workspaceOwnedByOwner() {
        Member owner = Member.builder().id(OWNER_ID).build();
        return Workspace.builder().id(WORKSPACE_ID).name("내 워크스페이스").member(owner).build();
    }

    @Test
    @DisplayName("다른 회원의 워크스페이스에는 트리를 생성할 수 없다")
    void createTreeInOthersWorkspace() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceOwnedByOwner()));

        assertAccessDenied(() -> treeService.createTree(OTHER_ID, WORKSPACE_ID));
        verify(treeRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 회원의 트리는 삭제할 수 없다")
    void deleteOthersTree() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspaceOwnedByOwner()));

        assertAccessDenied(() -> treeService.deleteTree(OTHER_ID, WORKSPACE_ID, TREE_ID));
        verify(treeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("트리 목록은 본인 워크스페이스의 트리만 조회한다")
    void getAllTreesIsScopedToMember() {
        when(treeRepository.findAllByWorkspace_Member_Id(OWNER_ID)).thenReturn(List.of());

        treeService.getAllTrees(OWNER_ID);

        verify(treeRepository).findAllByWorkspace_Member_Id(OWNER_ID);
        verify(treeRepository, never()).findAll();
    }

    @Test
    @DisplayName("본인 워크스페이스에는 트리를 생성할 수 있다")
    void ownerCanCreateTree() {
        Workspace workspace = workspaceOwnedByOwner();
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.existsByWorkspace(workspace)).thenReturn(false);
        when(treeRepository.save(any(Tree.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThatCode(() -> treeService.createTree(OWNER_ID, WORKSPACE_ID)).doesNotThrowAnyException();
    }

    private void assertAccessDenied(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(WorkspaceException.class)
                .satisfies(e -> assertThat(((WorkspaceException) e).getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED));
    }
}

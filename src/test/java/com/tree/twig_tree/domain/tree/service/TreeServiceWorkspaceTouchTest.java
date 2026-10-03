package com.tree.twig_tree.domain.tree.service;

import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 트리 생성/삭제 시 워크스페이스 updatedAt 이 갱신되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class TreeServiceWorkspaceTouchTest {

    private static final Long WORKSPACE_ID = 100L;
    private static final Long TREE_ID = 10L;

    @Mock
    private TreeRepository treeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @InjectMocks
    private TreeService treeService;

    private final Workspace workspace = Workspace.builder().id(WORKSPACE_ID).name("ws").build();

    @Test
    @DisplayName("트리 생성 시 워크스페이스 updatedAt 을 갱신한다")
    void createTreeTouchesWorkspace() {
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.existsByWorkspace(workspace)).thenReturn(false);

        treeService.createTree(WORKSPACE_ID);

        verify(workspaceRepository).touchById(eq(WORKSPACE_ID), any());
    }

    @Test
    @DisplayName("트리 삭제 시 워크스페이스 updatedAt 을 갱신한다")
    void deleteTreeTouchesWorkspace() {
        Tree tree = Tree.builder().id(TREE_ID).workspace(workspace).build();
        when(workspaceRepository.findById(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));

        treeService.deleteTree(WORKSPACE_ID, TREE_ID);

        verify(workspaceRepository).touchById(eq(WORKSPACE_ID), any());
    }
}

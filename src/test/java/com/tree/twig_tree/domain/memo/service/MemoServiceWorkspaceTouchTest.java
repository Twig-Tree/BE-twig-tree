package com.tree.twig_tree.domain.memo.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
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
 * 메모 수정/삭제 시 워크스페이스 updatedAt 이 갱신되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class MemoServiceWorkspaceTouchTest {

    private static final Long OWNER_ID = 1L;
    private static final Long TREE_ID = 10L;
    private static final Long NODE_ID = 1000L;

    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @InjectMocks
    private MemoService memoService;

    @BeforeEach
    void setUp() {
        Member owner = Member.builder().id(OWNER_ID).build();
        Workspace workspace = Workspace.builder().id(100L).member(owner).build();
        Tree tree = Tree.builder().id(TREE_ID).workspace(workspace).build();
        Node node = Node.builder().id(NODE_ID).name("노드").tree(tree).build();
        when(nodeRepository.findById(NODE_ID)).thenReturn(Optional.of(node));
    }

    @Test
    @DisplayName("메모 수정 시 워크스페이스 updatedAt 을 갱신한다")
    void updateMemoTouchesWorkspace() {
        memoService.updateMemo(OWNER_ID, NODE_ID, new MemoReqDTO.UpdateMemo("메모"));

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("메모 삭제 시 워크스페이스 updatedAt 을 갱신한다")
    void deleteMemoTouchesWorkspace() {
        memoService.deleteMemo(OWNER_ID, NODE_ID);

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }
}

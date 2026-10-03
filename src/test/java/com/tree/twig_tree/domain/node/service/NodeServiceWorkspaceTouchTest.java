package com.tree.twig_tree.domain.node.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.node.dto.NodeReqDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
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
 * 노드 생성/수정/삭제 시 워크스페이스 updatedAt 이 갱신되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class NodeServiceWorkspaceTouchTest {

    private static final Long OWNER_ID = 1L;
    private static final Long TREE_ID = 10L;
    private static final Long NODE_ID = 1000L;

    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private TreeRepository treeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @InjectMocks
    private NodeService nodeService;

    private final Member owner = Member.builder().id(OWNER_ID).build();
    private final Workspace workspace = Workspace.builder().id(100L).member(owner).build();
    private final Tree tree = Tree.builder().id(TREE_ID).workspace(workspace).build();
    private final Node node = Node.builder().id(NODE_ID).name("노드").tree(tree).build();

    @Test
    @DisplayName("노드 생성 시 워크스페이스 updatedAt 을 갱신한다")
    void createNodeTouchesWorkspace() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
        when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));

        nodeService.createNode(OWNER_ID, TREE_ID, new NodeReqDTO.CreateNode("루트", null, 1L));

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("노드 이름 수정 시 워크스페이스 updatedAt 을 갱신한다")
    void editNodeNameTouchesWorkspace() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
        when(nodeRepository.findById(NODE_ID)).thenReturn(Optional.of(node));

        nodeService.editNodeName(OWNER_ID, TREE_ID, NODE_ID, new NodeReqDTO.EditNodeName("새 이름"));

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("노드 삭제 시 워크스페이스 updatedAt 을 갱신한다")
    void deleteNodeTouchesWorkspace() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
        when(nodeRepository.findById(NODE_ID)).thenReturn(Optional.of(node));

        nodeService.deleteNode(OWNER_ID, TREE_ID, NODE_ID);

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }
}

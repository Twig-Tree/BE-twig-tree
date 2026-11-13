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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 노드 생성은 AI 트리 생성(GeneratedTreeWriter.saveToWorkspace)과 같은 워크스페이스 락을 잡아야 한다.
 *
 * <p>두 경로가 다른 락을 쓰면, 커밋되지 않은 루트 삽입을 트리 생성 쪽 노드 존재 검사가 보지 못해
 * 그 요청이 의도한 CHAT409-1 대신 uk_nodes_root_per_tree 위반으로 끝난다.
 */
@ExtendWith(MockitoExtension.class)
class NodeServiceCreateWorkspaceLockTest {

    private static final Long OWNER_ID = 1L;
    private static final Long WORKSPACE_ID = 100L;
    private static final Long TREE_ID = 10L;

    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private TreeRepository treeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @InjectMocks
    private NodeService nodeService;

    private final Member owner = Member.builder().id(OWNER_ID).build();
    private final Workspace workspace = Workspace.builder().id(WORKSPACE_ID).member(owner).build();
    private final Tree tree = Tree.builder().id(TREE_ID).workspace(workspace).build();

    @Test
    @DisplayName("루트 노드를 저장하기 전에 워크스페이스 락을 잡는다")
    void locksWorkspaceBeforeSavingRoot() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
        when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));

        nodeService.createNode(OWNER_ID, TREE_ID, new NodeReqDTO.CreateNode("루트", null, 1L));

        // 저장 뒤에 잡으면 트리 생성 쪽 검사와 겹치는 창이 남으므로 순서까지 확인한다.
        InOrder inOrder = Mockito.inOrder(workspaceRepository, nodeRepository);
        inOrder.verify(workspaceRepository).findByIdForUpdate(WORKSPACE_ID);
        inOrder.verify(nodeRepository).save(any(Node.class));
    }

    @Test
    @DisplayName("자식 노드 생성도 같은 락을 잡는다")
    void locksWorkspaceForChildNode() {
        Node root = Node.builder().id(500L).name("루트").tree(tree).build();
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
        when(nodeRepository.findById(500L)).thenReturn(Optional.of(root));
        when(nodeRepository.save(any(Node.class))).thenAnswer(inv -> inv.getArgument(0));

        nodeService.createNode(OWNER_ID, TREE_ID, new NodeReqDTO.CreateNode("자식", 500L, 1L));

        Mockito.verify(workspaceRepository).findByIdForUpdate(WORKSPACE_ID);
    }
}

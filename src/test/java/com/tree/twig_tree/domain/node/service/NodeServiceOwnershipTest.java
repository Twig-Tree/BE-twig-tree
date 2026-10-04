package com.tree.twig_tree.domain.node.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.exception.TreeException;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
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
 * 노드 소유권 검증. 다른 회원의 트리에 속한 노드는 노드에 접근하기 전에 TREE_ACCESS_DENIED 로 막혀야 한다.
 */
@ExtendWith(MockitoExtension.class)
class NodeServiceOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long TREE_ID = 10L;
    private static final Long NODE_ID = 1000L;

    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private TreeRepository treeRepository;
    @InjectMocks
    private NodeService nodeService;

    @BeforeEach
    void stubTreeLookup() {
        Member owner = Member.builder().id(OWNER_ID).build();
        Workspace workspace = Workspace.builder().id(100L).name("내 워크스페이스").member(owner).build();
        Tree tree = Tree.builder().id(TREE_ID).workspace(workspace).build();
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
    }

    @Test
    @DisplayName("다른 회원 트리의 노드는 삭제할 수 없다")
    void deleteOthersNode() {
        assertAccessDenied(() -> nodeService.deleteNode(OTHER_ID, TREE_ID, NODE_ID));
        verify(nodeRepository, never()).findById(any());
        verify(nodeRepository, never()).delete(any());
    }

    @Test
    @DisplayName("다른 회원의 트리는 조회할 수 없다")
    void getOthersTreeNodes() {
        assertAccessDenied(() -> nodeService.getFullTreeNodes(OTHER_ID, TREE_ID));
        verify(nodeRepository, never()).findFullTreeByTreeId(any());
    }

    @Test
    @DisplayName("본인 트리는 조회할 수 있다")
    void ownerCanReadTree() {
        when(nodeRepository.findFullTreeByTreeId(TREE_ID)).thenReturn(List.<Node>of());

        assertThatCode(() -> nodeService.getFullTreeNodes(OWNER_ID, TREE_ID)).doesNotThrowAnyException();
    }

    private void assertAccessDenied(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(TreeException.class)
                .satisfies(e -> assertThat(((TreeException) e).getErrorCode())
                        .isEqualTo(TreeErrorCode.TREE_ACCESS_DENIED));
    }
}

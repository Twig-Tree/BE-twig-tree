package com.tree.twig_tree.domain.memo.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.memo.dto.MemoReqDTO;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.NodeException;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 메모 소유권 검증. 다른 회원의 노드 메모는 NODE_ACCESS_DENIED 로 막히고 내용이 바뀌지 않아야 한다.
 */
@ExtendWith(MockitoExtension.class)
class MemoServiceOwnershipTest {

    private static final Long OWNER_ID = 1L;
    private static final Long OTHER_ID = 2L;
    private static final Long NODE_ID = 1000L;

    @Mock
    private NodeRepository nodeRepository;
    @InjectMocks
    private MemoService memoService;

    private Node node;

    @BeforeEach
    void stubNodeLookup() {
        Member owner = Member.builder().id(OWNER_ID).build();
        Workspace workspace = Workspace.builder().id(100L).name("내 워크스페이스").member(owner).build();
        Tree tree = Tree.builder().id(10L).workspace(workspace).build();
        node = Node.builder().id(NODE_ID).name("노드").memo("원래 메모").tree(tree).build();
        when(nodeRepository.findById(NODE_ID)).thenReturn(Optional.of(node));
    }

    @Test
    @DisplayName("다른 회원 노드의 메모는 수정할 수 없다")
    void updateOthersMemo() {
        assertAccessDenied(() -> memoService.updateMemo(OTHER_ID, NODE_ID, new MemoReqDTO.UpdateMemo("덮어쓰기")));
        assertThat(node.getMemo()).isEqualTo("원래 메모");
    }

    @Test
    @DisplayName("다른 회원 노드의 메모는 조회할 수 없다")
    void getOthersMemo() {
        assertAccessDenied(() -> memoService.getMemo(OTHER_ID, NODE_ID));
    }

    @Test
    @DisplayName("본인 노드의 메모는 수정할 수 있다")
    void ownerCanUpdateMemo() {
        memoService.updateMemo(OWNER_ID, NODE_ID, new MemoReqDTO.UpdateMemo("새 메모"));

        assertThat(node.getMemo()).isEqualTo("새 메모");
    }

    private void assertAccessDenied(ThrowingCallable action) {
        assertThatThrownBy(action)
                .isInstanceOf(NodeException.class)
                .satisfies(e -> assertThat(((NodeException) e).getErrorCode())
                        .isEqualTo(NodeErrorCode.NODE_ACCESS_DENIED));
    }
}

package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 워크스페이스 이름 충돌(uk_workspace_root_name 등) 같은 DB 제약조건 위반이
 * 채팅 도메인 에러(TREE_SAVE_FAILED)로 번역되는지 검증한다.
 *
 * <p>실제 랜덤 접미사 충돌을 재현하는 건 사실상 불가능하므로, 저장 계층에서
 * DataIntegrityViolationException이 나는 상황을 목(mock)으로 흉내낸다.
 */
@ExtendWith(MockitoExtension.class)
class GeneratedTreeWriterCollisionTest {

    @Mock
    private TreeRepository treeRepository;
    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;

    @Test
    void 워크스페이스_저장중_제약조건_위반이_TREE_SAVE_FAILED로_변환된다() {
        GeneratedTreeWriter writer = new GeneratedTreeWriter(treeRepository, nodeRepository, workspaceRepository);

        when(workspaceRepository.save(any(Workspace.class)))
            .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint \"uk_workspace_root_name\""));

        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            new LlmNode(1L, "루트", null, null, 1L)
        ));

        assertThatThrownBy(() -> writer.save(tree))
            .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.type(ChatException.class))
            .extracting(ChatException::getErrorCode)
            .isEqualTo(ChatErrorCode.TREE_SAVE_FAILED);
    }
}

package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeneratedTreeWriterTest {

    @Mock
    private TreeRepository treeRepository;
    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private MemberService memberService;

    @Test
    @DisplayName("중첩 트리를 부모부터 저장하고 형제 순서를 배열 위치로 매긴다")
    void savesParentsFirstWithSiblingOrder() {
        when(treeRepository.save(any(Tree.class))).thenAnswer(returnsFirstArg());
        when(nodeRepository.save(any(Node.class))).thenAnswer(returnsFirstArg());
        GeneratedTreeWriter writer =
            new GeneratedTreeWriter(treeRepository, nodeRepository, workspaceRepository, memberService);

        LlmTreeDTO tree = new LlmTreeDTO(new LlmNode("루트", "설명", List.of(
            new LlmNode("첫째", null, List.of(new LlmNode("손자", null, List.of()))),
            new LlmNode("둘째", null, List.of())
        )));

        TreeGenResDTO result = writer.save(1L, tree);

        ArgumentCaptor<Node> captor = ArgumentCaptor.forClass(Node.class);
        verify(nodeRepository, times(4)).save(captor.capture());
        List<Node> saved = captor.getAllValues();

        assertThat(saved).extracting(Node::getName).containsExactly("루트", "첫째", "둘째", "손자");
        assertThat(saved).extracting(Node::getOrderId).containsExactly(1L, 1L, 2L, 1L);
        assertThat(saved.get(0).getParent()).isNull();
        assertThat(saved.get(1).getParent()).isSameAs(saved.get(0));
        assertThat(saved.get(2).getParent()).isSameAs(saved.get(0));
        assertThat(saved.get(3).getParent()).isSameAs(saved.get(1));

        assertThat(result.nodes()).extracting(TreeGenResDTO.Node::name)
            .containsExactly("루트", "첫째", "둘째", "손자");
    }
}

package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 이미 있는 워크스페이스를 채우는 저장 경로({@link GeneratedTreeWriter#saveToWorkspace})와
 * LLM 호출 전 사전 검사({@link GeneratedTreeWriter#validateWritableWorkspace})를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeneratedTreeWriterWorkspaceTest {

    private static final Long MEMBER_ID = 1L;
    private static final Long OTHER_MEMBER_ID = 2L;
    private static final Long WORKSPACE_ID = 7L;
    private static final Long TREE_ID = 70L;

    @Mock
    private TreeRepository treeRepository;
    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @Mock
    private MemberService memberService;

    private GeneratedTreeWriter writer;

    @BeforeEach
    void setUp() {
        writer = new GeneratedTreeWriter(treeRepository, nodeRepository, workspaceRepository, memberService);
        when(treeRepository.save(any(Tree.class))).thenAnswer(returnsFirstArg());
        when(nodeRepository.save(any(Node.class))).thenAnswer(returnsFirstArg());
    }

    private Workspace workspace(Long ownerId, String name) {
        return Workspace.builder()
            .id(WORKSPACE_ID)
            .name(name)
            .member(Member.builder().id(ownerId).build())
            .build();
    }

    private LlmTreeDTO tree() {
        return new LlmTreeDTO(new LlmNode("루트", "설명", List.of(
            new LlmNode("첫째", null, List.of()),
            new LlmNode("둘째", null, List.of())
        )));
    }

    // --- 워크스페이스 상태별 처리 ---

    @Test
    @DisplayName("트리가 없으면 트리를 만들고 노드를 저장한다")
    void createsTreeWhenMissing() {
        Workspace workspace = workspace(MEMBER_ID, "내 워크스페이스");
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.findByWorkspace(workspace)).thenReturn(Optional.empty());

        TreeGenResDTO result = writer.saveToWorkspace(MEMBER_ID, WORKSPACE_ID, tree());

        verify(treeRepository).save(any(Tree.class));
        verify(nodeRepository, org.mockito.Mockito.times(3)).save(any(Node.class));
        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(result.nodes()).extracting(TreeGenResDTO.Node::name).containsExactly("루트", "첫째", "둘째");
    }

    @Test
    @DisplayName("트리는 있고 노드가 없으면 그 트리에 노드를 저장한다")
    void reusesEmptyTree() {
        Workspace workspace = workspace(MEMBER_ID, "내 워크스페이스");
        Tree existing = Tree.builder().id(TREE_ID).workspace(workspace).build();
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.findByWorkspace(workspace)).thenReturn(Optional.of(existing));
        when(nodeRepository.existsByTree_Id(TREE_ID)).thenReturn(false);

        TreeGenResDTO result = writer.saveToWorkspace(MEMBER_ID, WORKSPACE_ID, tree());

        verify(treeRepository, never()).save(any(Tree.class));
        assertThat(result.treeId()).isEqualTo(TREE_ID);
        assertThat(result.workspaceId()).isEqualTo(WORKSPACE_ID);
    }

    @Test
    @DisplayName("트리에 노드가 있으면 WORKSPACE_TREE_NOT_EMPTY 로 거절한다")
    void rejectsTreeWithNodes() {
        Workspace workspace = workspace(MEMBER_ID, "내 워크스페이스");
        Tree existing = Tree.builder().id(TREE_ID).workspace(workspace).build();
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.findByWorkspace(workspace)).thenReturn(Optional.of(existing));
        when(nodeRepository.existsByTree_Id(TREE_ID)).thenReturn(true);

        assertError(() -> writer.saveToWorkspace(MEMBER_ID, WORKSPACE_ID, tree()),
            ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);

        verify(nodeRepository, never()).save(any(Node.class));
    }

    @Test
    @DisplayName("없는 워크스페이스면 WORKSPACE_NOT_FOUND")
    void workspaceNotFound() {
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.empty());

        assertError(() -> writer.saveToWorkspace(MEMBER_ID, WORKSPACE_ID, tree()),
            WorkspaceErrorCode.WORKSPACE_NOT_FOUND);

        verify(nodeRepository, never()).save(any(Node.class));
    }

    @Test
    @DisplayName("내 워크스페이스가 아니면 WORKSPACE_ACCESS_DENIED")
    void notMyWorkspace() {
        Workspace workspace = workspace(OTHER_MEMBER_ID, "남의 워크스페이스");
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));

        assertError(() -> writer.saveToWorkspace(MEMBER_ID, WORKSPACE_ID, tree()),
            WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);

        verify(treeRepository, never()).findByWorkspace(any());
        verify(nodeRepository, never()).save(any(Node.class));
    }

    // --- 그 밖의 저장 규칙 ---

    @Test
    @DisplayName("사용자가 지은 워크스페이스 이름은 바꾸지 않는다")
    void keepsWorkspaceName() {
        Workspace workspace = workspace(MEMBER_ID, "내가 지은 이름");
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.findByWorkspace(workspace)).thenReturn(Optional.empty());

        writer.saveToWorkspace(MEMBER_ID, WORKSPACE_ID, tree());

        assertThat(workspace.getName()).isEqualTo("내가 지은 이름");
    }

    @Test
    @DisplayName("저장 후 워크스페이스 updatedAt 을 갱신한다")
    void touchesWorkspace() {
        Workspace workspace = workspace(MEMBER_ID, "내 워크스페이스");
        when(workspaceRepository.findByIdForUpdate(WORKSPACE_ID)).thenReturn(Optional.of(workspace));
        when(treeRepository.findByWorkspace(workspace)).thenReturn(Optional.empty());

        writer.saveToWorkspace(MEMBER_ID, WORKSPACE_ID, tree());

        verify(workspaceRepository).touchById(eq(WORKSPACE_ID), any(Instant.class));
    }

    // --- LLM 호출 전 사전 검사 ---

    @Test
    @DisplayName("사전 검사: 트리가 없으면 통과한다")
    void preCheckPassesWithoutTree() {
        when(workspaceRepository.findOwnerMemberIdById(WORKSPACE_ID)).thenReturn(Optional.of(MEMBER_ID));
        when(treeRepository.findIdByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.empty());

        writer.validateWritableWorkspace(MEMBER_ID, WORKSPACE_ID);
    }

    @Test
    @DisplayName("사전 검사: 빈 트리만 있으면 통과한다")
    void preCheckPassesWithEmptyTree() {
        when(workspaceRepository.findOwnerMemberIdById(WORKSPACE_ID)).thenReturn(Optional.of(MEMBER_ID));
        when(treeRepository.findIdByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(TREE_ID));
        when(nodeRepository.existsByTree_Id(TREE_ID)).thenReturn(false);

        writer.validateWritableWorkspace(MEMBER_ID, WORKSPACE_ID);
    }

    @Test
    @DisplayName("사전 검사: 노드가 있는 트리면 WORKSPACE_TREE_NOT_EMPTY")
    void preCheckRejectsTreeWithNodes() {
        when(workspaceRepository.findOwnerMemberIdById(WORKSPACE_ID)).thenReturn(Optional.of(MEMBER_ID));
        when(treeRepository.findIdByWorkspaceId(WORKSPACE_ID)).thenReturn(Optional.of(TREE_ID));
        when(nodeRepository.existsByTree_Id(TREE_ID)).thenReturn(true);

        assertError(() -> writer.validateWritableWorkspace(MEMBER_ID, WORKSPACE_ID),
            ChatErrorCode.WORKSPACE_TREE_NOT_EMPTY);
    }

    @Test
    @DisplayName("사전 검사: 없는 워크스페이스면 WORKSPACE_NOT_FOUND")
    void preCheckWorkspaceNotFound() {
        when(workspaceRepository.findOwnerMemberIdById(WORKSPACE_ID)).thenReturn(Optional.empty());

        assertError(() -> writer.validateWritableWorkspace(MEMBER_ID, WORKSPACE_ID),
            WorkspaceErrorCode.WORKSPACE_NOT_FOUND);
    }

    @Test
    @DisplayName("사전 검사: 내 워크스페이스가 아니면 WORKSPACE_ACCESS_DENIED")
    void preCheckAccessDenied() {
        when(workspaceRepository.findOwnerMemberIdById(WORKSPACE_ID)).thenReturn(Optional.of(OTHER_MEMBER_ID));

        assertError(() -> writer.validateWritableWorkspace(MEMBER_ID, WORKSPACE_ID),
            WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);

        verify(treeRepository, never()).findIdByWorkspaceId(any());
    }

    private void assertError(Runnable action, BaseErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOfAny(ChatException.class, WorkspaceException.class)
            .satisfies(e -> assertThat(((com.tree.twig_tree.global.apiPayload.exception.ProjectException) e)
                .getErrorCode()).isEqualTo(expected));
    }
}

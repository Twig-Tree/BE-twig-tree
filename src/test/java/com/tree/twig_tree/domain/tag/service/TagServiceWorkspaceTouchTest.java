package com.tree.twig_tree.domain.tag.service;

import com.tree.twig_tree.domain.member.entity.Member;
import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tag.dto.TagReqDTO;
import com.tree.twig_tree.domain.tag.entity.NodeTag;
import com.tree.twig_tree.domain.tag.entity.Tag;
import com.tree.twig_tree.domain.tag.repository.NodeTagRepository;
import com.tree.twig_tree.domain.tag.repository.TagRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
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
 * 태그 생성/수정/삭제, 노드 태그 추가/제거 시 워크스페이스 updatedAt 이 갱신되어야 한다.
 */
@ExtendWith(MockitoExtension.class)
class TagServiceWorkspaceTouchTest {

    private static final Long OWNER_ID = 1L;
    private static final Long TREE_ID = 10L;
    private static final Long NODE_ID = 1000L;
    private static final Long TAG_ID = 2000L;

    @Mock
    private TreeRepository treeRepository;
    @Mock
    private NodeRepository nodeRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private NodeTagRepository nodeTagRepository;
    @Mock
    private WorkspaceRepository workspaceRepository;
    @InjectMocks
    private TagService tagService;

    private Tree tree;
    private Node node;
    private Tag tag;

    @BeforeEach
    void setUp() {
        Member owner = Member.builder().id(OWNER_ID).build();
        Workspace workspace = Workspace.builder().id(100L).member(owner).build();
        tree = Tree.builder().id(TREE_ID).workspace(workspace).build();
        node = Node.builder().id(NODE_ID).name("노드").tree(tree).build();
        tag = Tag.builder().id(TAG_ID).name("태그").tree(tree).build();
    }

    @Test
    @DisplayName("태그 생성 시 워크스페이스 updatedAt 을 갱신한다")
    void createTagTouchesWorkspace() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));

        tagService.createTag(OWNER_ID, TREE_ID, new TagReqDTO.CreateTag("태그"));

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("태그 이름 수정 시 워크스페이스 updatedAt 을 갱신한다")
    void updateTagNameTouchesWorkspace() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag));

        tagService.updateTagName(OWNER_ID, TREE_ID, TAG_ID, new TagReqDTO.EditTagName("새 태그"));

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("태그 삭제 시 워크스페이스 updatedAt 을 갱신한다")
    void deleteTagTouchesWorkspace() {
        when(treeRepository.findById(TREE_ID)).thenReturn(Optional.of(tree));
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag));

        tagService.deleteTag(OWNER_ID, TREE_ID, TAG_ID);

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("노드에 태그 추가 시 워크스페이스 updatedAt 을 갱신한다")
    void addNodeTagTouchesWorkspace() {
        when(nodeRepository.findById(NODE_ID)).thenReturn(Optional.of(node));
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag));

        tagService.addNodeTag(OWNER_ID, NODE_ID, TAG_ID);

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }

    @Test
    @DisplayName("노드에서 태그 제거 시 워크스페이스 updatedAt 을 갱신한다")
    void removeNodeTagTouchesWorkspace() {
        when(nodeRepository.findById(NODE_ID)).thenReturn(Optional.of(node));
        when(tagRepository.findById(TAG_ID)).thenReturn(Optional.of(tag));
        when(nodeTagRepository.findByNodeAndTag(node, tag))
                .thenReturn(Optional.of(NodeTag.builder().node(node).tag(tag).build()));

        tagService.removeNodeTag(OWNER_ID, NODE_ID, TAG_ID);

        verify(workspaceRepository).touchByTreeId(eq(TREE_ID), any());
    }
}

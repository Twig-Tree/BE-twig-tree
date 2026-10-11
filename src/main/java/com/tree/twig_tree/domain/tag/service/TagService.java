package com.tree.twig_tree.domain.tag.service;

import com.tree.twig_tree.domain.node.entity.Node;
import com.tree.twig_tree.domain.node.exception.NodeException;
import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.domain.node.repository.NodeRepository;
import com.tree.twig_tree.domain.tag.converter.TagConverter;
import com.tree.twig_tree.domain.tag.dto.TagReqDTO;
import com.tree.twig_tree.domain.tag.dto.TagResDTO;
import com.tree.twig_tree.domain.tag.entity.NodeTag;
import com.tree.twig_tree.domain.tag.entity.Tag;
import com.tree.twig_tree.domain.tag.exception.TagException;
import com.tree.twig_tree.domain.tag.exception.code.TagErrorCode;
import com.tree.twig_tree.domain.tag.repository.NodeTagRepository;
import com.tree.twig_tree.domain.tag.repository.TagRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.exception.TreeException;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TagService {

    private final TreeRepository treeRepository;
    private final NodeRepository nodeRepository;
    private final TagRepository tagRepository;
    private final NodeTagRepository nodeTagRepository;

    @Transactional
    public TagResDTO.GetTag createTag(Long memberId, Long treeId, TagReqDTO.CreateTag dto) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);
        Tag tag = Tag.builder()
                .name(dto.name())
                .tree(tree)
                .build();

        tagRepository.save(tag);
        return TagConverter.toGetTag(tag);
    }

    @Transactional
    public TagResDTO.GetTag updateTagName(Long memberId, Long treeId, Long tagId, TagReqDTO.EditTagName dto) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);
        Tag tag = validateTag(tagId);
        validateTagInTree(treeId, tag);

        tag.updateName(dto.name());
        return TagConverter.toGetTag(tag);
    }

    @Transactional
    public Void deleteTag(Long memberId, Long treeId, Long tagId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);
        Tag tag = validateTag(tagId);
        validateTagInTree(treeId, tag);

        tagRepository.delete(tag);
        return null;
    }

    public List<TagResDTO.GetTag> getTreeTags(Long memberId, Long treeId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);
        List<Tag> tags = tagRepository.findAllByTree(tree);
        return TagConverter.toGetTags(tags);
    }

    public List<TagResDTO.GetTag> getNodeTags(Long memberId, Long nodeId) {
        Node node = validateNode(nodeId);
        validateTreeOwner(memberId, node.getTree());
        List<Tag> tags = tagRepository.findAllByNode(node);
        return TagConverter.toGetTags(tags);
    }

    @Transactional
    public TagResDTO.GetTag addNodeTag(Long memberId, Long nodeId, Long tagId) {
        Node node = validateNode(nodeId);
        validateTreeOwner(memberId, node.getTree());
        Tag tag = validateTag(tagId);
        validateTagInTree(node.getTree().getId(), tag);
        NodeTag nodeTag = NodeTag.builder().node(node).tag(tag).build();

        nodeTagRepository.save(nodeTag);
        return TagConverter.toGetTag(tag);
    }

    @Transactional
    public Void removeNodeTag(Long memberId, Long nodeId, Long tagId) {
        Node node = validateNode(nodeId);
        validateTreeOwner(memberId, node.getTree());
        Tag tag = validateTag(tagId);
        NodeTag nodeTag = nodeTagRepository.findByNodeAndTag(node, tag).orElseThrow(() -> new TagException(TagErrorCode.NODE_TAG_NOT_FOUND));
        nodeTagRepository.delete(nodeTag);
        return null;
    }

    // 검증 함수

    // 트리 검증
    private Tree validateTree(Long treeId) {
        return treeRepository.findById(treeId).orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
    }

    // 노드 검증
    private Node validateNode(Long nodeId) {
        return nodeRepository.findById(nodeId).orElseThrow(()->new NodeException(NodeErrorCode.NODE_NOT_FOUND));
    }

    // 태그 검증
    private Tag validateTag(Long tagId) {
        return tagRepository.findById(tagId).orElseThrow(() -> new TagException(TagErrorCode.TAG_NOT_FOUND));
    }

    // 트리 소유자 검증
    private void validateTreeOwner(Long memberId, Tree tree) {
        if (!tree.getWorkspace().getMember().getId().equals(memberId)) {
            throw new TreeException(TreeErrorCode.TREE_ACCESS_DENIED);
        }
    }

    // 태그-트리 소유 검증
    // 내 트리 & 남의 태그로 요청하는 경우 다른 사람의 태그 정보를 바꿀 수 있는 경우를 막습니다.
    private void validateTagInTree(Long treeId, Tag tag) {
        if (!tag.getTree().getId().equals(treeId)) {
            throw new TagException(TagErrorCode.TAG_NOT_IN_TREE);
        }
    }
}

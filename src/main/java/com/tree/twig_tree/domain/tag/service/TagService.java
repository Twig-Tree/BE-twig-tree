package com.tree.twig_tree.domain.tag.service;

import com.tree.twig_tree.domain.tag.converter.TagConverter;
import com.tree.twig_tree.domain.tag.dto.TagReqDTO;
import com.tree.twig_tree.domain.tag.dto.TagResDTO;
import com.tree.twig_tree.domain.tag.entity.Tag;
import com.tree.twig_tree.domain.tag.exception.TagException;
import com.tree.twig_tree.domain.tag.exception.code.TagErrorCode;
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
    private final TagRepository tagRepository;

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
        tag.updateName(dto.name());
        tagRepository.save(tag);
        return TagConverter.toGetTag(tag);
    }

    @Transactional
    public Void deleteTag(Long memberId, Long treeId, Long tagId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);
        Tag tag = validateTag(tagId);
        tagRepository.delete(tag);
        return null;
    }

    public List<TagResDTO.GetTag> getTreeTags(Long memberId, Long treeId) {
        Tree tree = validateTree(treeId);
        validateTreeOwner(memberId, tree);
        return List.of();
    }

    public List<TagResDTO.GetTag> getNodeTags(Long memberId, Long nodeId) {
        return List.of();
    }

    @Transactional
    public List<TagResDTO.GetTag> addNodeTag(Long memberId, Long nodeId) {
        return List.of();
    }

    @Transactional
    public List<TagResDTO.GetTag> removeNodeTag(Long memberId, Long nodeId) {
        return List.of();
    }

    // 검증 함수

    // 트리 검증
    private Tree validateTree(Long treeId) {
        return treeRepository.findById(treeId).orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));
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
}

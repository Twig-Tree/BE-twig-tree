package com.tree.twig_tree.domain.tag.service;

import com.tree.twig_tree.domain.tag.dto.TagResDTO;
import com.tree.twig_tree.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepository tagRepository;

    public TagResDTO.GetTag createTag(Long memberId, Long treeId) {
        return null;
    }

    public TagResDTO.GetTag updateTagName(Long memberId, Long treeId, Long tagId) {
        return null;
    }

    public Void deleteTag(Long memberId, Long treeId, Long tagId) {
        return null;
    }

    public List<TagResDTO.GetTag> getTreeTags(Long memberId, Long treeId) {
        return List.of();
    }

    public List<TagResDTO.GetTag> getNodeTags(Long memberId, Long nodeId) {
        return List.of();
    }

    public List<TagResDTO.GetTag> updateNodeTags(Long memberId, Long nodeId) {
        return null;
    }
}

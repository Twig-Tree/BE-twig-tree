package com.tree.twig_tree.domain.tag.converter;

import com.tree.twig_tree.domain.tag.dto.TagResDTO;
import com.tree.twig_tree.domain.tag.entity.Tag;

public class TagConverter {
    public static TagResDTO.GetTag toGetTag(Tag tag) {
        return TagResDTO.GetTag.builder()
                .tagId(tag.getId())
                .name(tag.getName())
                .build();
    }
}

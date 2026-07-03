package com.tree.twig_tree.domain.folder.dto;

import lombok.Builder;

public class FolderResDTO {

    @Builder
    public record FolderId(
            Long folderId
    ) {}


    public record GetFolder (
            Long folderId,
            String name
    ){}
}

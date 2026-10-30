package com.tree.twig_tree.domain.folder.dto;

import lombok.Builder;

import java.util.List;

public class FolderResDTO {

    @Builder
    public record FolderId(
            Long folderId
    ) {}

    @Builder
    public record GetFolder (
            Long folderId,
            String name,
            Long folderParentId
    ){}

    @Builder
    public record FolderPathList(
            List<FolderPathItem> path
    ) {
        public record FolderPathItem(
                Long folderId,
                String name
        ) {}
    }

}

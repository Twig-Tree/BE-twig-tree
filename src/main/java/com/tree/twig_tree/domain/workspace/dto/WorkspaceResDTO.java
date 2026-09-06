package com.tree.twig_tree.domain.workspace.dto;

import lombok.Builder;

import java.time.LocalDateTime;

public class WorkspaceResDTO {

    @Builder
    public record WorkspaceId (
            Long workspaceId
    ){}

    @Builder
    public record GetWorkspace (
            Long workspaceId,
            String name,
            Long folderId,
            Long treeId,
            LocalDateTime updatedAt
    ){}
}

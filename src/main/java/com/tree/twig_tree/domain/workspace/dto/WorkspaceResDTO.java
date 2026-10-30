package com.tree.twig_tree.domain.workspace.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

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
            Instant updatedAt
    ){}

    // 커서 기반 워크스페이스 목록 응답
    @Builder
    public record GetWorkspaceSlice (
            List<GetWorkspace> workspaces,
            String nextCursor,
            boolean hasNext
    ){}
}

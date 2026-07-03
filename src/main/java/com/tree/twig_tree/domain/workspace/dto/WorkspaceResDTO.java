package com.tree.twig_tree.domain.workspace.dto;

public class WorkspaceResDTO {
    public record WorkspaceId (
            Long workspaceId
    ){}

    public record GetWorkspace (
            Long workspaceId,
            String name

            // TODO:
            // 생성일자
            // 수정일자
    ){}
}

package com.tree.twig_tree.domain.workspace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class WorkspaceReqDTO {
    public record CreateWorkspace (
            @NotBlank
            String name,

            @Min(1) // null 가능
            Long folderId
    ){}
}

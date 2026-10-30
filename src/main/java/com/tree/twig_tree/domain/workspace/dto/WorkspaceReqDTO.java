package com.tree.twig_tree.domain.workspace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class WorkspaceReqDTO {
    public record CreateWorkspace (
            @NotBlank
            @Size(max = 30)
            String name,

            @Min(1) // null 가능
            Long folderId
    ){}

    public record UpdateWorkspace (
            @NotBlank
            @Size(max = 30)
            String name
    ){}
}

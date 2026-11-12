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

    /**
     * 워크스페이스 이동 요청
     * folderId 가 null 이면 어떠한 폴더에도 속하지 않은 최상위로 이동합니다.
     */
    public record MoveWorkspace (
            @Min(1) // null 가능 (루트로 이동)
            Long folderId
    ){}
}

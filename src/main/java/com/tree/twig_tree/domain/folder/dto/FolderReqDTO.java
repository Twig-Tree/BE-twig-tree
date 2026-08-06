package com.tree.twig_tree.domain.folder.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FolderReqDTO {

    public record CreateFolder (
            @NotBlank
            @Size(max = 30)
            String name,

            @Min(1) // null 가능
            Long folderParentId
    ){}

    public record UpdateFolder (
            @NotBlank
            @Size(max = 30)
            String name
    ){}


}

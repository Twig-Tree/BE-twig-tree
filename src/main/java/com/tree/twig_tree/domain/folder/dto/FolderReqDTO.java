package com.tree.twig_tree.domain.folder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public class FolderReqDTO {

    public record CreateFolder (
            @NotBlank
            @Max(15)
            String name,

            @Min(1) // null 가능
            Long folderParentId
    ){}

    public record UpdateFolder (
            @NotBlank
            String name
    ){}


}

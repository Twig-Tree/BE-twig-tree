package com.tree.twig_tree.domain.folder.converter;

import com.tree.twig_tree.domain.folder.dto.FolderProjection;
import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;

import java.util.List;

public class FolderConverter {
    public static List<FolderResDTO.GetFolder> toGetFolders(List<Folder> folders) {
        return folders.stream()
                .map(FolderConverter::toGetFolder).toList();
    }

    public static FolderResDTO.GetFolder toGetFolder(Folder folder) {
        return FolderResDTO.GetFolder.builder()
                .folderId(folder.getId())
                .name(folder.getName())
                .folderParentId(folder.getParent() != null ? folder.getParent().getId() : null)
                .build();

    }

    // Projection -> Record 변환
    public static FolderResDTO.FolderPathList toGetFoldersPath(List<FolderProjection.FolderAncestorProjection> ancestors) {
        return FolderResDTO.FolderPathList.builder()
                .path(ancestors.stream()
                        .map(a -> new FolderResDTO.FolderPathList.FolderPathItem(a.getFolderId(), a.getName()))
                        .toList())
                .build();
    }
}

package com.tree.twig_tree.domain.folder.converter;

import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;

import java.util.List;

public class FolderConverter {
    public static List<FolderResDTO.GetFolder> getFolders(List<Folder> folders) {
        return folders.stream()
                .map(folder -> new FolderResDTO.GetFolder(
                        folder.getId(),
                        folder.getName()
                )).toList();
    }
}

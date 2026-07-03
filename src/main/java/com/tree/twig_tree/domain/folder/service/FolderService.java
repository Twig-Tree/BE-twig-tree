package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    private final FolderRepository folderRepository;

    /**
     * 노드 생성
     * @param dto
     * @return
     */
    @Transactional
    public FolderResDTO.FolderId createFolder(FolderReqDTO.createFolder dto) {

        Folder parentFolder = null;
        if (dto.parentFolderId() != null) {
            parentFolder = folderRepository.findById(dto.parentFolderId()).orElseThrow(() -> new FolderException(FolderErrorCode.PARENT_NOT_FOUND));
        }

        Folder newFolder = Folder.builder()
                .parent(parentFolder)
                .name(dto.name())
                .build();

        folderRepository.save(newFolder);

        return new FolderResDTO.FolderId(newFolder.getId());
    }
}

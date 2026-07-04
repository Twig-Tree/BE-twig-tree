package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.domain.folder.converter.FolderConverter;
import com.tree.twig_tree.domain.folder.dto.FolderReqDTO;
import com.tree.twig_tree.domain.folder.dto.FolderResDTO;
import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FolderService {

    private final FolderRepository folderRepository;

    /**
     * 폴더 목록 조회
     * @param parentFolderId
     * @return
     */
    public List<FolderResDTO.GetFolder> getFolders(Long parentFolderId) {
        List<Folder> folders;
        if (parentFolderId == null) {
            folders = folderRepository.findAllByParentIsNull();
        } else {
            Folder parentFolder = folderRepository.findById(parentFolderId).orElseThrow(()->new FolderException(FolderErrorCode.PARENT_NOT_FOUND));
            folders = folderRepository.findAllByParent(parentFolder);
        }

        return FolderConverter.getFolders(folders);
    }

    /**
     * 폴더 생성
     * @param dto
     * @return
     */
    @Transactional
    public FolderResDTO.FolderId createFolder(FolderReqDTO.CreateFolder dto) {

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

    /**
     * 폴더 이름 수정
     * @param folderId
     * @param dto
     * @return
     */
    @Transactional
    public FolderResDTO.FolderId updateFolder(Long folderId, FolderReqDTO.UpdateFolder dto) {

        Folder folder = folderRepository.findById(folderId).orElseThrow(()-> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));

        folder.updateName(dto.name());

        return new FolderResDTO.FolderId(folder.getId());
    }

    /**
     * 폴더 삭제
     * @param folderId
     * @return
     */
    @Transactional
    public Void deleteFolder(Long folderId) {
        Folder folder = folderRepository.findById(folderId).orElseThrow(()-> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));
        folderRepository.delete(folder);

        return null;
    }
}

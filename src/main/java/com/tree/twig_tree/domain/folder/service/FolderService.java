package com.tree.twig_tree.domain.folder.service;

import com.tree.twig_tree.domain.folder.converter.FolderConverter;
import com.tree.twig_tree.domain.folder.dto.FolderProjection;
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
     * 폴더 상위 경로 목록 조회
     * @param folderId
     * @return
     */
    public FolderResDTO.FolderPathList getFoldersPath(Long folderId) {
        validateFolder(folderId);

        // 해당 폴더ID로 상위 연결된 부모의 폴더들을 찾기 // Recursive CTE로 한번에 조회
        List<FolderProjection.FolderAncestorProjection> ancestors = folderRepository.findAncestorPathRaw(folderId);

        return FolderConverter.toGetFoldersPath(ancestors);

    }

    /**
     * 폴더 목록 조회
     * @param folderParentId
     * @return
     */
    public List<FolderResDTO.GetFolder> getFolders(Long folderParentId) {
        List<Folder> folders;
        if (folderParentId == null) {
            folders = folderRepository.findAllByParentIsNull();
        } else {
            Folder parent = folderRepository.findById(folderParentId).orElseThrow(()->new FolderException(FolderErrorCode.PARENT_NOT_FOUND));
            folders = folderRepository.findAllByParent(parent);
        }

        return FolderConverter.toGetFolders(folders);
    }

    /**
     * 폴더 생성
     * @param dto
     * @return
     */
    @Transactional
    public FolderResDTO.GetFolder createFolder(FolderReqDTO.CreateFolder dto) {

        Folder parent = null;
        if (dto.folderParentId() != null) {
            parent = folderRepository.findById(dto.folderParentId()).orElseThrow(() -> new FolderException(FolderErrorCode.PARENT_NOT_FOUND));
        }

        Folder newFolder = Folder.builder()
                .parent(parent)
                .name(dto.name())
                .build();

        folderRepository.save(newFolder);

        return FolderConverter.toGetFolder(newFolder);
    }

    /**
     * 폴더 조회
     * @param folderId
     * @return
     */
    public FolderResDTO.GetFolder getFolder(Long folderId) {

        Folder folder = validateFolder(folderId);

        return FolderConverter.toGetFolder(folder);
    }

    /**
     * 폴더 이름 수정
     * @param folderId
     * @param dto
     * @return
     */
    @Transactional
    public FolderResDTO.GetFolder updateFolder(Long folderId, FolderReqDTO.UpdateFolder dto) {

        Folder folder = validateFolder(folderId);

        folder.updateName(dto.name());

        return FolderConverter.toGetFolder(folder);
    }

    /**
     * 폴더 삭제
     * @param folderId
     * @return
     */
    @Transactional
    public Void deleteFolder(Long folderId) {
        Folder folder = validateFolder(folderId);
        folderRepository.delete(folder);

        return null;
    }

    private Folder validateFolder(Long folderId) {
        return folderRepository.findById(folderId).orElseThrow(()-> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));
    }
}

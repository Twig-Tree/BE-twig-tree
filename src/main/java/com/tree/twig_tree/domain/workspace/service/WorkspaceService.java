package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final FolderRepository folderRepository;
    private final WorkspaceRepository workspaceRepository;

    public List<WorkspaceResDTO.GetWorkspace> getWorkspaces(Long parentFolderId) {


        return List.of();
    }

    /**
     * 워크스페이스 생성
     * @param dto
     * @return
     */
    @Transactional
    public WorkspaceResDTO.WorkspaceId createWorkspace(WorkspaceReqDTO.CreateWorkspace dto) {

        // 폴더가 있는지 확인
        Folder parentFolder = null;
        if (dto.folderId() != null) {
            parentFolder = folderRepository.findById(dto.folderId()).orElseThrow(()-> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));
        }

        Workspace workspace = Workspace.builder()
                .name(dto.name())
                .folder(parentFolder)
                .build();

        workspaceRepository.save(workspace);

        return new WorkspaceResDTO.WorkspaceId(workspace.getId());
    }

    public WorkspaceResDTO.GetWorkspace getWorkspace(Long workspaceId) {
        return null;
    }

    @Transactional
    public WorkspaceResDTO.WorkspaceId updateWorkspace(Long workspaceId) {
        return null;
    }

    @Transactional
    public Void deleteWorkspace(Long workspaceId) {
        return null;
    }
}

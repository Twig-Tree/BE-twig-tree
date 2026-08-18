package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.workspace.converter.WorkspaceConverter;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceReqDTO;
import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;
import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
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

    /**
     * 특정 폴더 내의 워크스페이스 목록 조회
     * @param folderId null이면 폴더에 속하지 않는 최상위 워크스페이스
     * @return
     */
    public List<WorkspaceResDTO.GetWorkspace> getWorkspaces(Long folderId) {

        if (folderId != null && !folderRepository.existsById(folderId)) {
            throw new FolderException(FolderErrorCode.FOLDER_NOT_FOUND);
        }

        List<Workspace> workspaceList;
        if (folderId == null) {
            workspaceList = workspaceRepository.findAllByFolderIsNullOrderByUpdatedAtDesc();
        } else {
            workspaceList = workspaceRepository.findAllByFolder_IdOrderByUpdatedAtDesc(folderId);
        }

        return WorkspaceConverter.toGetWorkspaces(workspaceList);
    }

    /**
     * 워크스페이스 생성
     * @param dto
     * @return
     */
    @Transactional
    public WorkspaceResDTO.GetWorkspace createWorkspace(WorkspaceReqDTO.CreateWorkspace dto) {

        // 폴더가 있는지 확인
        Folder folder = null;
        if (dto.folderId() != null) {
            folder = folderRepository.findById(dto.folderId()).orElseThrow(()-> new FolderException(FolderErrorCode.FOLDER_NOT_FOUND));
        }

        Workspace workspace = Workspace.builder()
                .name(dto.name())
                .folder(folder)
                .build();

        workspaceRepository.save(workspace);

        return WorkspaceConverter.toGetWorkspace(workspace);
    }

    /**
     * 워크스페이스 조회
     * @param workspaceId
     * @return
     */
    public WorkspaceResDTO.GetWorkspace getWorkspace(Long workspaceId) {

        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(()->new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        return WorkspaceConverter.toGetWorkspace(workspace);
    }

    /**
     * 워크스페이스 이름 수정
     * @param workspaceId
     * @return
     */
    @Transactional
    public WorkspaceResDTO.GetWorkspace updateWorkspace(Long workspaceId, String name) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(()->new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        workspace.updateName(name);
        workspaceRepository.flush();

        return WorkspaceConverter.toGetWorkspace(workspace);
    }

    /**
     * 워크스페이스 삭제
     * @param workspaceId
     * @return
     */
    @Transactional
    public Void deleteWorkspace(Long workspaceId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(()->new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        workspaceRepository.delete(workspace);
        return null;
    }
}

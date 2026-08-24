package com.tree.twig_tree.domain.workspace.service;

import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.folder.repository.FolderRepository;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
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
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkspaceService {

    private final FolderRepository folderRepository;
    private final WorkspaceRepository workspaceRepository;
    private final TreeRepository treeRepository;

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
            workspaceList = workspaceRepository.findAllByFolderIsNull();
        } else {
            workspaceList = workspaceRepository.findAllByFolder_Id(folderId);
        }

        Map<Long, Long> treeIdByWorkspaceId = treeRepository.findAllByWorkspaceIn(workspaceList).stream()
                .collect(Collectors.toMap(tree -> tree.getWorkspace().getId(), Tree::getId));

        return WorkspaceConverter.toGetWorkspaces(workspaceList, treeIdByWorkspaceId);
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

        // 생성 직후 워크스페이스에는 트리가 있을 수 없음 -> null
        return WorkspaceConverter.toGetWorkspace(workspace, null);
    }

    /**
     * 워크스페이스 조회
     * @param workspaceId
     * @return
     */
    public WorkspaceResDTO.GetWorkspace getWorkspace(Long workspaceId) {

        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(()->new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));

        Long treeId = treeRepository.findByWorkspace(workspace).map(Tree::getId).orElse(null);

        return WorkspaceConverter.toGetWorkspace(workspace, treeId);
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

        Long treeId = treeRepository.findByWorkspace(workspace).map(Tree::getId).orElse(null);

        return WorkspaceConverter.toGetWorkspace(workspace, treeId);
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

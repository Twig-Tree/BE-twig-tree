package com.tree.twig_tree.domain.tree.service;

import com.tree.twig_tree.domain.folder.entity.Folder;
import com.tree.twig_tree.domain.folder.exception.FolderException;
import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.domain.tree.converter.TreeConverter;
import com.tree.twig_tree.domain.tree.dto.TreeResDTO;
import com.tree.twig_tree.domain.tree.entity.Tree;
import com.tree.twig_tree.domain.tree.exception.TreeException;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
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
public class TreeService {

    private final TreeRepository treeRepository;
    private final WorkspaceRepository workspaceRepository;

    // TODO: 사용자가 생성한 모든 트리만 조회하도록 제한
    /**
     * 모든 트리 조회
     * @return List<Tree>
     */
    public List<TreeResDTO.TreeId> getAllTrees(Long memberId) {
        List<Tree> treeList = treeRepository.findAllByWorkspace_Member_Id(memberId);
        return TreeConverter.toGetAllTrees(treeList);
    }

    /**
     * 트리 생성
     * @return treeId
     */
    @Transactional
    public TreeResDTO.TreeId createTree(Long memberId, Long workspaceId) {

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateWorkspaceOwner(memberId, workspace);

        // 해당 워크스페이스에 이미 트리가 존재함
        if (treeRepository.existsByWorkspace(workspace)) {
            throw new TreeException(TreeErrorCode.TREE_ALREADY_EXISTS);
        }

        Tree tree = Tree.builder()
                .workspace(workspace)
                .build();

        treeRepository.save(tree);
        return TreeResDTO.TreeId.builder()
                .treeId(tree.getId())
                .build();
    }

    /**
     * 트리 삭제
     * @param treeId
     */
    @Transactional
    public void deleteTree(Long memberId, Long workspaceId, Long treeId) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElseThrow(() -> new WorkspaceException(WorkspaceErrorCode.WORKSPACE_NOT_FOUND));
        validateWorkspaceOwner(memberId, workspace);
        Tree tree = treeRepository.findById(treeId).orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));

        validateWorkspaceTree(workspaceId, tree);

        treeRepository.delete(tree);
    }

    // 검증 함수

    // 트리가 해당 워크스페이스에 속하지 않음
    private void validateWorkspaceTree(Long workspaceId, Tree tree) {
        if (!tree.getWorkspace().getId().equals(workspaceId)) {
            throw new TreeException(TreeErrorCode.TREE_NOT_IN_WORKSPACE);
        }
    }

    // 워크스페이스 소유자 검증  // 워크스페이스 소유자이면 반드시 트리 소유자이다.
    private void validateWorkspaceOwner(Long memberId, Workspace workspace) {
        if (!workspace.getMember().getId().equals(memberId)) {
            throw new WorkspaceException(WorkspaceErrorCode.WORKSPACE_ACCESS_DENIED);
        }
    }
}

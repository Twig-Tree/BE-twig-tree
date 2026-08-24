package com.tree.twig_tree.domain.workspace.converter;

import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;

import java.util.List;
import java.util.Map;

public class WorkspaceConverter {
    public static List<WorkspaceResDTO.GetWorkspace> toGetWorkspaces(List<Workspace> workspaceList, Map<Long, Long> treeIdByWorkspaceId) {

        return workspaceList.stream()
                .map(workspace -> toGetWorkspace(workspace, treeIdByWorkspaceId.get(workspace.getId())))
                .toList();
    }

    public static WorkspaceResDTO.GetWorkspace toGetWorkspace(Workspace workspace, Long treeId) {

        return WorkspaceResDTO.GetWorkspace.builder()
                .workspaceId(workspace.getId())
                .name(workspace.getName())
                .folderId(workspace.getFolder() != null ? workspace.getFolder().getId() : null)
                .treeId(treeId)
                .build();
    }

}

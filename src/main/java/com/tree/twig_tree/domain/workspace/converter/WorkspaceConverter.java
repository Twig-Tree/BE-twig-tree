package com.tree.twig_tree.domain.workspace.converter;

import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;

import java.util.List;

public class WorkspaceConverter {
    public static List<WorkspaceResDTO.GetWorkspace> toGetWorkspaces(List<Workspace> workspaceList) {

        return workspaceList.stream()
                .map(WorkspaceConverter::toGetWorkspace).toList();
    }

    public static WorkspaceResDTO.GetWorkspace toGetWorkspace(Workspace workspace) {

        return WorkspaceResDTO.GetWorkspace.builder()
                .workspaceId(workspace.getId())
                .name(workspace.getName())
                .folderId(workspace.getFolder() != null ? workspace.getFolder().getId() : null)
                .updatedAt(workspace.getUpdatedAt())
                .build();
    }

}

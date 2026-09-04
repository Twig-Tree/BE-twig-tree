package com.tree.twig_tree.domain.workspace.converter;

import com.tree.twig_tree.domain.workspace.dto.WorkspaceResDTO;
import com.tree.twig_tree.domain.workspace.entity.Workspace;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

public class WorkspaceConverter {

    // 저장값은 UTC 기준 LocalDateTime(서버 타임존 UTC 고정). 응답에는 KST 오프셋을 명시해 내려준다.
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

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
                .updatedAt(toKst(workspace.getUpdatedAt()))
                .treeId(treeId)
                .build();
    }

    // UTC LocalDateTime -> KST(+09:00) OffsetDateTime (같은 시각을 오프셋만 바꿔 표현)
    private static OffsetDateTime toKst(LocalDateTime utc) {
        if (utc == null) {
            return null;
        }
        return utc.atOffset(ZoneOffset.UTC).withOffsetSameInstant(KST);
    }
}

package com.tree.twig_tree.domain.workspace.dto;

import lombok.Builder;

import java.time.OffsetDateTime;

public class WorkspaceResDTO {

    @Builder
    public record WorkspaceId (
            Long workspaceId
    ){}

    @Builder
    public record GetWorkspace (
            Long workspaceId,
            String name,
            Long folderId,
            Long treeId,

            // 저장값은 UTC(LocalDateTime, 서버 타임존 UTC 고정 - TwigTreeApplication 참고)이며,
            // WorkspaceConverter 에서 KST(+09:00) 오프셋을 명시한 OffsetDateTime 으로 변환해 내려준다.
            // 예: 2026-09-04T15:00:00+09:00 (ISO-8601, 오프셋이 있어 클라이언트가 명확하게 해석 가능)
            OffsetDateTime updatedAt
    ){}
}

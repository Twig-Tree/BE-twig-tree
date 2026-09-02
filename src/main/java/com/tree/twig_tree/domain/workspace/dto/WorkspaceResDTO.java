package com.tree.twig_tree.domain.workspace.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;

import java.time.LocalDateTime;

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

            // 값 자체는 UTC 기준 LocalDateTime(서버 타임존을 UTC로 고정함, TwigTreeApplication 참고).
            // 브라우저가 오프셋 없는 문자열을 로컬 시간대로 잘못 해석하지 않도록 'Z'를 명시적으로 붙여 내려준다.
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            LocalDateTime updatedAt
    ){}
}

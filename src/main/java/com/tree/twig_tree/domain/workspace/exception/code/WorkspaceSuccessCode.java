package com.tree.twig_tree.domain.workspace.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WorkspaceSuccessCode implements BaseSuccessCode {

    WORKSPACE_FOUND(HttpStatus.OK, "WORKSPACE200-1", "성공적으로 워크스페이스를 조회했습니다."),
    WORKSPACES_FOUND(HttpStatus.OK, "WORKSPACE200-2", "성공적으로 워크스페이스 목록을 조회했습니다."),
    WORKSPACE_CREATED(HttpStatus.CREATED, "WORKSPACE201-1", "성공적으로 워크스페이스를 추가했습니다."),
    WORKSPACE_UPDATED(HttpStatus.OK, "WORKSPACE200-3", "성공적으로 워크스페이스 정보를 수정했습니다."),
    WORKSPACE_DELETED(HttpStatus.OK, "WORKSPACE200-4", "성공적으로 워크스페이스를 삭제했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
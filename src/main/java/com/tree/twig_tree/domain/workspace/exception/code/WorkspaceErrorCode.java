package com.tree.twig_tree.domain.workspace.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum WorkspaceErrorCode implements BaseErrorCode {

    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "WORKSPACE404-1","해당 워크스페이스가 존재하지 않습니다."),
    DUPLICATE_WORKSPACE_NAME(HttpStatus.BAD_REQUEST, "WORKSPACE400-1","같은 레벨에서 동일한 이름의 워크스페이스는 만들 수 없습니다."),
    WORKSPACE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "WORKSPACE403-1", "본인 소유 워크스페이스가 아니므로 처리할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

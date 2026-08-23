package com.tree.twig_tree.domain.tree.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TreeErrorCode implements BaseErrorCode{

    TREE_NOT_FOUND(HttpStatus.NOT_FOUND, "TREE404-1","해당 트리가 존재하지 않습니다."),
    TREE_NOT_IN_WORKSPACE(HttpStatus.FORBIDDEN, "TREE403-1", "해당 워크스페이스에 속하지 않는 트리입니다."),
    TREE_ALREADY_EXISTS(HttpStatus.CONFLICT, "TREE409-1", "해당 워크스페이스에 이미 트리가 존재합니다. 하나의 워크스페이스에는 하나의 트리만 존재할 수 있습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}



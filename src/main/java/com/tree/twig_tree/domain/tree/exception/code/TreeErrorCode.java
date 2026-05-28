package com.tree.twig_tree.domain.tree.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TreeErrorCode implements BaseErrorCode{

    TREE_NOT_FOUND(HttpStatus.NOT_FOUND, "TREE404","해당 트리가 존재하지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}



package com.tree.twig_tree.domain.memo.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemoErrorCode implements BaseErrorCode {;

    private final HttpStatus status;
    private final String code;
    private final String message;
}

package com.tree.twig_tree.global.apiPayload.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
public class ProjectException extends RuntimeException {
    private final BaseErrorCode errorCode;

    // 기본 생성자: BaseErrorCode의 메시지를 부모(RuntimeException)에게 전달
    public ProjectException(BaseErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

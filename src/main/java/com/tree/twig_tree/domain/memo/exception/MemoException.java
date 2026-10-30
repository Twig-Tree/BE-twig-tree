package com.tree.twig_tree.domain.memo.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class MemoException extends ProjectException {
    public MemoException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

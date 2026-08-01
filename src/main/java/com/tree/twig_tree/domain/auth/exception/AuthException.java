package com.tree.twig_tree.domain.auth.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class AuthException extends ProjectException {
    public AuthException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

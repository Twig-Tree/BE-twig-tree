package com.tree.twig_tree.domain.tag.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class TagException extends ProjectException {
    public TagException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

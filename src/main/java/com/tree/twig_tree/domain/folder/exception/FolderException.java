package com.tree.twig_tree.domain.folder.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class FolderException extends ProjectException {
    public FolderException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

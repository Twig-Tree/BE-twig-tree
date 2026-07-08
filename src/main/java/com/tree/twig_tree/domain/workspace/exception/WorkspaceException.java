package com.tree.twig_tree.domain.workspace.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class WorkspaceException extends ProjectException {
    public WorkspaceException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

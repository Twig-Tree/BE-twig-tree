package com.tree.twig_tree.domain.lecture.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class LectureException extends ProjectException {
    public LectureException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

package com.tree.twig_tree.domain.chat.exception;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;

public class ChatException extends ProjectException {
    public ChatException(BaseErrorCode errorCode) {
        super(errorCode);
    }
}

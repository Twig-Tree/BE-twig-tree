package com.tree.twig_tree.domain.chat.exception;

import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.Getter;

@Getter
public class ChatException extends RuntimeException {

    private final ChatErrorCode errorCode;

    public ChatException(ChatErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

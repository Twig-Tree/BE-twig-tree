package com.tree.twig_tree.domain.chat.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatSuccessCode implements BaseSuccessCode {

    TREE_GENERATED(HttpStatus.OK, "CHAT201-1", "트리가 성공적으로 생성되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

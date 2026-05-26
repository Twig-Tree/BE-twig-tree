package com.tree.twig_tree.domain.node.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NodeErrorCode implements BaseErrorCode {

    NODE_NOT_FOUND(HttpStatus.NOT_FOUND, "NODE404","해당 노드가 존재하지 않습니다."),
    PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "NODE404","부모 노드가 존재하지 않습니다."),

    NODE_NOT_INCLUDED_IN_TREE(HttpStatus.BAD_REQUEST, "NODE400","해당 트리에 속하지 않은 노드입니다");



    private final HttpStatus status;
    private final String code;
    private final String message;
}

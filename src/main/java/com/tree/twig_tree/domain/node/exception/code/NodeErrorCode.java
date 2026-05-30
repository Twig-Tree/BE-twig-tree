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
    NODE_NOT_INCLUDED_IN_TREE(HttpStatus.BAD_REQUEST, "NODE400","해당 트리에 속하지 않은 노드입니다"),
    DUPLICATED_ORDER_ID(HttpStatus.CONFLICT, "NODE409" , "같은 부모를 갖는 노드끼리는 order_id가 겹칠 수 없습니다."),
    ONE_ROOT_PER_TREE(HttpStatus.CONFLICT, "NODE409" , "하나의 트리에는 하나의 루트만 존재할 수 있습니다. ");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

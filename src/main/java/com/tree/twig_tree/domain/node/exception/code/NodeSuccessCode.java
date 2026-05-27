package com.tree.twig_tree.domain.node.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NodeSuccessCode implements BaseSuccessCode {

    NODE_FOUND(HttpStatus.OK, "NODE200", "성공적으로 노드를 조회했습니다."),
    NODES_FOUND(HttpStatus.OK, "NODE200", "성공적으로 노드 목록을 조회했습니다."),
    NODE_CREATED(HttpStatus.CREATED, "NODE201", "성공적으로 노드를 추가했습니다."),
    NODE_UPDATED(HttpStatus.OK, "NODE200", "성공적으로 노드 정보를 수정했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

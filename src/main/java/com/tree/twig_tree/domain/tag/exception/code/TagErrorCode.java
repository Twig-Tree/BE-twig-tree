package com.tree.twig_tree.domain.tag.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TagErrorCode implements BaseErrorCode {
    TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "TAG404-1", "해당 태그가 존재하지 않습니다."),
    DUPLICATE_TAG_NAME(HttpStatus.CONFLICT, "TAG409-1","같은 트리에서 동일한 이름의 태그는 만들 수 없습니다."),
    DUPLICATE_NODE_TAG(HttpStatus.CONFLICT, "TAG409-2", "이미 노드에 추가된 태그입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

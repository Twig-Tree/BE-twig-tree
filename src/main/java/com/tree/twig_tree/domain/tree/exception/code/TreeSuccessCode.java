package com.tree.twig_tree.domain.tree.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TreeSuccessCode implements BaseSuccessCode {

    TREE_FOUND(HttpStatus.OK, "TREE200", "성공적으로 트리를 조회했습니다."),
    TREES_FOUND(HttpStatus.OK, "TREE200", "성공적으로 트리 목록을 조회했습니다."),
    TREE_CREATED(HttpStatus.CREATED, "TREE201", "성공적으로 트리를 추가했습니다."),
    TREE_UPDATED(HttpStatus.OK, "TREE200", "성공적으로 트리 정보를 수정했습니다."),
    TREE_DELETED(HttpStatus.NO_CONTENT, "TREE204", "성공적으로 트리를 삭제했습니다.");


    private final HttpStatus status;
    private final String code;
    private final String message;
}

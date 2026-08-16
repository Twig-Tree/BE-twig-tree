package com.tree.twig_tree.domain.memo.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemoSuccessCode implements BaseSuccessCode {

    MEMO_FOUND(HttpStatus.OK, "MEMO200-1", "성공적으로 메모를 조회했습니다."),
    MEMO_UPDATED(HttpStatus.OK, "MEMO200-2", "성공적으로 메모를 수정했습니다."),
    MEMO_DELETED(HttpStatus.OK, "MEMO200-3", "성공적으로 메모를 삭제했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

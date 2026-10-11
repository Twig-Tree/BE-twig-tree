package com.tree.twig_tree.domain.tag.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TagSuccessCode implements BaseSuccessCode {

    TAG_CREATED(HttpStatus.CREATED, "TAG201-1", "성공적으로 태그를 추가했습니다."),
    TAG_UPDATED(HttpStatus.OK, "TAG200-1", "성공적으로 태그 정보를 수정했습니다."),
    TAG_DELETED(HttpStatus.OK, "TAG200-2", "성공적으로 태그를 삭제했습니다."),
    TAGS_FOUND(HttpStatus.OK, "TAG200-3", "성공적으로 태그 목록을 조회했습니다."),
    TAG_ADDED(HttpStatus.OK, "TAG200-4", "성공적으로 노드에 태그가 추가되었습니다."),
    TAG_REMOVED(HttpStatus.OK, "TAG200-5", "성공적으로 노드에 태그가 제거되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

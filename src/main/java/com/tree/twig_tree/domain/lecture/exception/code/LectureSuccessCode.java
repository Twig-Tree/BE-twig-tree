package com.tree.twig_tree.domain.lecture.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LectureSuccessCode implements BaseSuccessCode {

    /** 생성한 시나리오를 저장하지 않으므로 201 이 아니라 200 이다. */
    LECTURE_SCENARIO_GENERATED(HttpStatus.OK, "LECTURE200-1", "강의 시나리오가 생성되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

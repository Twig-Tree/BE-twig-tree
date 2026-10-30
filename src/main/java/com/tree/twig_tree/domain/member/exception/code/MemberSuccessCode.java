package com.tree.twig_tree.domain.member.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberSuccessCode implements BaseSuccessCode {

    ME_FOUND(HttpStatus.OK, "MEMBER200-1", "성공적으로 내 정보를 조회했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

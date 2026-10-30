package com.tree.twig_tree.domain.member.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements BaseErrorCode {

    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER404-1", "존재하지 않는 회원입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

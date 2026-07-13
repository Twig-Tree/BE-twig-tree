package com.tree.twig_tree.domain.auth.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseSuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthSuccessCode implements BaseSuccessCode {

    LOGIN_OK(HttpStatus.OK, "AUTH200-1", "성공적으로 로그인했습니다."),
    REFRESH_OK(HttpStatus.OK, "AUTH200-2", "성공적으로 토큰을 재발급했습니다."),
    LOGOUT_OK(HttpStatus.OK, "AUTH200-3", "성공적으로 로그아웃했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

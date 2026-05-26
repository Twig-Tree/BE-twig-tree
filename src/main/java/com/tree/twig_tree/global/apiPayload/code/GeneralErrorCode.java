package com.tree.twig_tree.global.apiPayload.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum GeneralErrorCode implements BaseErrorCode{

    BAD_REQUEST(HttpStatus.BAD_REQUEST, "COMMON400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON401", "인증되지 않았습니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON403", "접근이 금지되었습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON404", "해당 리소스를 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON500", "서버 내부 오류입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

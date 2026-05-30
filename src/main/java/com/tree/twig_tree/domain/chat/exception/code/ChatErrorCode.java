package com.tree.twig_tree.domain.chat.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "CHAT400-1", "지원하지 않는 AI 제공자입니다."),
    LLM_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT500-1", "AI 모델 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

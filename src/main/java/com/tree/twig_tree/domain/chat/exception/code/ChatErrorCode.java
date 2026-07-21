package com.tree.twig_tree.domain.chat.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode implements BaseErrorCode {

    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "CHAT400-1", "지원하지 않는 AI 제공자입니다."),
    EMPTY_MESSAGE(HttpStatus.BAD_REQUEST, "CHAT400-2", "요청 메시지가 비어 있습니다."),
    LLM_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT500-1", "AI 모델 호출에 실패했습니다."),
    LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "CHAT504-1", "AI 모델 응답 시간이 초과되었습니다."),
    LLM_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "CHAT502-1", "AI 모델이 올바른 형식의 트리를 생성하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

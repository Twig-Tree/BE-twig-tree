package com.tree.twig_tree.domain.chat.exception.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatErrorCode {
    UNSUPPORTED_PROVIDER("지원하지 않는 AI 제공자입니다."),
    LLM_CALL_FAILED("AI 모델 호출에 실패했습니다.");

    private final String message;
}

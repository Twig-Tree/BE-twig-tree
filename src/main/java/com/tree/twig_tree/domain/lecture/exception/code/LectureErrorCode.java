package com.tree.twig_tree.domain.lecture.exception.code;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LectureErrorCode implements BaseErrorCode {

    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "LECTURE400-1",
        "강의 시나리오 생성을 지원하지 않는 AI 제공자입니다."),
    SCHEMA_VERSION_MISMATCH(HttpStatus.BAD_REQUEST, "LECTURE400-2",
        "요청한 시나리오 스키마 버전이 서버의 버전과 다릅니다. 최신 버전으로 다시 시도해주세요."),
    REPAIR_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "LECTURE400-3",
        "허용된 repair 횟수를 초과했습니다."),
    LLM_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "LECTURE500-1",
        "AI 모델 호출에 실패했습니다."),
    RESPONSE_TRUNCATED(HttpStatus.BAD_GATEWAY, "LECTURE502-1",
        "AI 모델 응답이 길이 상한에 걸려 잘렸습니다. 요청 범위를 좁혀주세요."),
    MODEL_REFUSED(HttpStatus.BAD_GATEWAY, "LECTURE502-2",
        "AI 모델이 요청을 거절했습니다."),
    RESPONSE_EMPTY(HttpStatus.BAD_GATEWAY, "LECTURE502-3",
        "AI 모델이 빈 응답을 반환했습니다."),
    LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "LECTURE504-1",
        "AI 모델 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

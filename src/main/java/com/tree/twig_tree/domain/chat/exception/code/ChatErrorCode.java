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
    INPUT_REQUIRED(HttpStatus.BAD_REQUEST, "CHAT400-3", "메시지와 파일 중 최소 하나는 필요합니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "CHAT400-4", "지원하지 않는 파일 형식입니다. txt, md, pdf, docx, hwp, hwpx 파일만 업로드할 수 있습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "CHAT400-5", "파일 크기가 상한을 초과했습니다. (txt·md 1MB, pdf·docx·hwp·hwpx 10MB)"),
    FILE_TEXT_TOO_LONG(HttpStatus.BAD_REQUEST, "CHAT400-6", "파일 내용이 너무 깁니다. 20,000자 이내로 줄여주세요."),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "CHAT400-7", "파일에 읽을 수 있는 내용이 없습니다. 스캔한 이미지 PDF 는 글자를 추출할 수 없습니다."),
    FILE_READ_FAILED(HttpStatus.BAD_REQUEST, "CHAT400-8", "파일을 읽을 수 없습니다. UTF-8로 인코딩된 텍스트 파일인지 확인해주세요."),
    FILE_PARSE_FAILED(HttpStatus.BAD_REQUEST, "CHAT400-9", "문서를 해석할 수 없습니다. 파일이 손상되었거나 확장자와 실제 형식이 다른지 확인해주세요."),
    FILE_ENCRYPTED(HttpStatus.BAD_REQUEST, "CHAT400-10", "암호가 설정된 문서는 읽을 수 없습니다. 암호를 해제한 뒤 업로드해주세요."),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "CHAT400-11", "요청 메시지가 너무 깁니다. 500자 이내로 줄여주세요."),
    LLM_CALL_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "CHAT500-1", "AI 모델 호출에 실패했습니다."),
    LLM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "CHAT504-1", "AI 모델 응답 시간이 초과되었습니다."),
    LLM_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "CHAT502-1", "AI 모델이 올바른 형식의 트리를 생성하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

package com.tree.twig_tree.global.apiPayload.handler;

import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;
import com.tree.twig_tree.global.apiPayload.exception.ProjectException;
import com.tree.twig_tree.global.apiPayload.util.ConstraintErrorCodeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GeneralExceptionAdvice {

    /**
     * log: 어느 핸들러에서 예외가 발생했는지 서버에 로그를 남기면 좋습니다.
     */

    // 인증/인가에서 문제 발생 시 예외 처리
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthorizationDenied(
            AuthorizationDeniedException e
    ) {
        BaseErrorCode code = GeneralErrorCode.FORBIDDEN;
        return ApiResponse.onFailure(code, null);
    }

    // 프로젝트에서 발생한 예외 처리
    @ExceptionHandler(ProjectException.class)
    public ResponseEntity<ApiResponse<Void>> handleProjectException(
            ProjectException e
    ) {
        log.error("ProjectException: {}", e.getMessage());

        BaseErrorCode errorCode = e.getErrorCode();
        return ApiResponse.onFailure(errorCode, null);

    }

    // @Validation 검증 실패 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e
    ) {

        // 검증 실패한 변수명과  실패 이유를 담을 Map
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });

        BaseErrorCode code = GeneralErrorCode.BAD_REQUEST;
        return ApiResponse.onFailure(code, errors);
    }



    // DB 제약조건 위반 시 예외 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<String>> handleDataIntegrityViolationException(
            DataIntegrityViolationException e
    ) {
        log.error("handleDataIntegrityViolationException: {}", e.getMessage());

        // 도메인 별 제약조건 에러코드 매핑
        BaseErrorCode code = ConstraintErrorCodeMapper.getErrorCode(e);

        return ApiResponse.onFailure(code, "데이터 제약조건을 위반했습니다.");
    }


    /**
     * 컨테이너 레벨 업로드 상한(spring.servlet.multipart) 초과 시 예외 처리.
     * 이 상한은 안전망이고, 정상 범위의 초과는 각 도메인에서 더 구체적인 코드로 먼저 걸러진다.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<String>> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException e
    ) {
        log.warn("업로드 크기 초과: {}", e.getMessage());

        BaseErrorCode code = GeneralErrorCode.BAD_REQUEST;
        return ApiResponse.onFailure(code, "업로드 가능한 파일 크기를 초과했습니다.");

    }


    // 그 외의 정의되지 않은 모든 예외 처리
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleException(
            Exception ex
    ) {
        log.error("정의되지 않은 예외: {}", ex.getMessage(), ex);

        BaseErrorCode code = GeneralErrorCode.INTERNAL_SERVER_ERROR;
        return ApiResponse.onFailure(code, null);

    }
}

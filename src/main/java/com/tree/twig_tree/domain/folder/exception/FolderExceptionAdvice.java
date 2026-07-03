package com.tree.twig_tree.domain.folder.exception;

import com.tree.twig_tree.domain.folder.exception.code.FolderErrorCode;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// GeneralExceptionAdvice보다 FolderExceptionAdvice가 먼저 처리되도록 합니다.
@Order(Ordered.HIGHEST_PRECEDENCE)

// 범위 지정 - 폴더 패키지에서 발생한 예외만 FolderExceptionAdvice가 먼저 확인합니다.
@RestControllerAdvice(basePackages = "com.tree.twig_tree.domain.folder")
@Slf4j
public class FolderExceptionAdvice {

    // 폴더의 name 중복 제약조건 예외 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        String message = e.getMessage();
        log.error("DataIntegrityViolation message: {}", e.getMessage()); // 추가

        if (message == null) throw e;

        // 루트 폴더 or 부모가 있는 일반 폴더 name이 겹치면 안됩니다.
        if (message.contains("uk_folder_root_name") || message.contains("uk_folder_parent_name") )  {
            BaseErrorCode code = FolderErrorCode.DUPLICATE_FOLDER_NAME;
            return ResponseEntity.status(code.getStatus())
                    .body(ApiResponse.onFailure(code, null));
        }

        throw e; // 폴더 관련 아니면 던짐
    }
}

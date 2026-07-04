package com.tree.twig_tree.domain.workspace.exception;

import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// GeneralExceptionAdvice보다 WorkspaceExceptionAdvice가 먼저 처리되도록 합니다.
@Order(Ordered.HIGHEST_PRECEDENCE)

// 범위 지정 - 워크스페이스 패키지에서 발생한 예외만 WorkspaceExceptionAdvice가 먼저 확인합니다.
@RestControllerAdvice(basePackages = "com.tree.twig_tree.domain.workspace")
@Slf4j
public class WorkspaceExceptionAdvice {

    // 워크스페이스의 name 중복 제약조건 예외 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        String message = e.getMessage();
        log.error("DataIntegrityViolation message: {}", e.getMessage()); // 추가

        if (message == null) throw e;

        // 루트 워크스페이스 or 폴더가 있는 일반 워크스페이스 name이 겹치면 안됩니다.
        if (message.contains("uk_workspace_root_name") || message.contains("uk_workspace_folder_name") )  {
            BaseErrorCode code = WorkspaceErrorCode.DUPLICATE_WORKSPACE_NAME;

            return ResponseEntity.status(code.getStatus())
                    .body(ApiResponse.onFailure(code, null));
        }

        throw e; // 워크스페이스 관련 아니면 던짐
    }
}

package com.tree.twig_tree.domain.node.exception;

import com.tree.twig_tree.domain.node.exception.code.NodeErrorCode;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// GeneralExceptionAdvice보다 NodeExceptionAdvice가 먼저 처리되도록 합니다.
@Order(Ordered.HIGHEST_PRECEDENCE)

// 범위 지정 - 노드 패키지에서 발생한 예외만 NodeExceptionAdvice가 먼저 확인합니다.
@RestControllerAdvice(basePackages = "com.tree.twig_tree.domain.node")
public class NodeExceptionAdvice {

    // 노드의 orderId 제약조건 예외 처리
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException e
    ) {
        String message = e.getMessage();

        if (message != null && message.contains("parent_id") && message.contains("order_id")) {
            BaseErrorCode code = NodeErrorCode.DUPLICATED_ORDER_ID;
            return ResponseEntity.status(code.getStatus())
                    .body(ApiResponse.onFailure(code, null));
        }

        throw e; // 노드 관련 아니면 던짐
    }
}

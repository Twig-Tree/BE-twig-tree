package com.tree.twig_tree.global.apiPayload.swagger;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link ApiErrorCodeExample} 로 선언된 도메인 에러코드와, 모든 엔드포인트에 공통으로 적용되는
 * 에러코드(인증/검증/서버 오류)를 모아 Swagger 응답 예시로 붙인다.
 */
@Component
public class ErrorCodeOperationCustomizer implements OperationCustomizer {

    private static final String APPLICATION_JSON = "application/json";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        List<BaseErrorCode> errorCodes = new ArrayList<>();

        for (ApiErrorCodeExample annotation : handlerMethod.getMethod().getAnnotationsByType(ApiErrorCodeExample.class)){

        }
        return operation; //
    }
}

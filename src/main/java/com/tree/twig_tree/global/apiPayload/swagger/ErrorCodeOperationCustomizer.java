package com.tree.twig_tree.global.apiPayload.swagger;

import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.*;

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

        for (ApiErrorCodeExample annotation : handlerMethod.getMethod().getAnnotationsByType(ApiErrorCodeExample.class)) {
            errorCodes.addAll(resolve(annotation));
        }

        if (requiresAuthentication(handlerMethod)) {
            errorCodes.add(GeneralErrorCode.UNAUTHORIZED);
            errorCodes.add(GeneralErrorCode.FORBIDDEN);
        }

        if (hasValidatedParameter(handlerMethod)) {
            errorCodes.add(GeneralErrorCode.BAD_REQUEST);
        }

        errorCodes.add(GeneralErrorCode.INTERNAL_SERVER_ERROR);

        addExamples(operation, errorCodes);
        return operation;
    }

    private List<BaseErrorCode> resolve(ApiErrorCodeExample annotation) {
        BaseErrorCode[] constants = annotation.value().getEnumConstants();

        if (annotation.only().length == 0) {
            return List.of(constants);
        }

        Set<String> only = Set.of(annotation.only());
        List<BaseErrorCode> filtered = new ArrayList<>();
        for (BaseErrorCode constant : constants) {
            if (only.contains(((Enum<?>) constant).name())) {
                filtered.add(constant);
            }
        }
        return filtered;
    }

    private boolean requiresAuthentication(HandlerMethod handlerMethod) {
        return handlerMethod.getMethodAnnotation(PublicApi.class) == null;
    }

    private boolean hasValidatedParameter(HandlerMethod handlerMethod) {
        for (MethodParameter parameter : handlerMethod.getMethodParameters()) {
            if (parameter.hasParameterAnnotation(Valid.class)) {
                return true;
            }
        }
        return false;
    }

    private void addExamples(Operation operation, List<BaseErrorCode> errorCodes) {
        ApiResponses responses = operation.getResponses();

        Map<Integer, List<BaseErrorCode>> byStatus = new LinkedHashMap<>();
        for (BaseErrorCode errorCode : errorCodes) {
            byStatus.computeIfAbsent(errorCode.getStatus().value(), key -> new ArrayList<>()).add(errorCode);
        }

        byStatus.forEach((status, codes) -> {
            String statusKey = String.valueOf(status);
            io.swagger.v3.oas.models.responses.ApiResponse response = responses.get(statusKey);
            if (response == null) {
                response = new io.swagger.v3.oas.models.responses.ApiResponse()
                        .description(HttpStatus.valueOf(status).getReasonPhrase());
                responses.addApiResponse(statusKey, response);
            }

            Content content = response.getContent();
            if (content == null) {
                content = new Content();
                response.setContent(content);
            }

            MediaType mediaType = content.get(APPLICATION_JSON);
            if (mediaType == null) {
                mediaType = new MediaType().schema(new Schema<>().type("object"));
                content.addMediaType(APPLICATION_JSON, mediaType);
            }

            for (BaseErrorCode errorCode : codes) {
                mediaType.addExamples(errorCode.getCode(), new Example()
                        .summary(errorCode.getMessage())
                        .value(exampleBody(errorCode)));
            }
        });
    }

    private Map<String, Object> exampleBody(BaseErrorCode errorCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("isSuccess", false);
        body.put("code", errorCode.getCode());
        body.put("message", errorCode.getMessage());
        body.put("data", null);
        return body;
    }
}

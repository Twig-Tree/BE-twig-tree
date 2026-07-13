package com.tree.twig_tree.global.security.handler;

import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        GeneralErrorCode errorCode = GeneralErrorCode.FORBIDDEN;
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.onFailure(errorCode, null));
    }

}

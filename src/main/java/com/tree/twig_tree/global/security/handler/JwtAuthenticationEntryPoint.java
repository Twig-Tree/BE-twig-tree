package com.tree.twig_tree.global.security.handler;

import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.apiPayload.code.BaseErrorCode;
import com.tree.twig_tree.global.apiPayload.code.GeneralErrorCode;
import com.tree.twig_tree.global.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        Object attribute = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        BaseErrorCode errorCode = attribute instanceof BaseErrorCode code ? code : GeneralErrorCode.UNAUTHORIZED;

        writeErrorResponse(response, errorCode);
    }

    private void writeErrorResponse(HttpServletResponse response, BaseErrorCode errorCode) throws IOException {
        response.setStatus(errorCode.getStatus().value());
        response.setContentType("application/json;charset=UTF8");
        objectMapper.writeValue(response.getWriter(), ApiResponse.onFailure(errorCode, null));
    }

}

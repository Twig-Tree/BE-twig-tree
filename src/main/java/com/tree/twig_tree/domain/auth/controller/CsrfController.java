package com.tree.twig_tree.domain.auth.controller;

import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.code.AuthSuccessCode;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth")
@RestController
public class CsrfController {

    @GetMapping("/auth/csrf")
    @Operation(summary = "CSRF 토큰 발급", description = "로그인 전에도 호출할 수 있습니다. 쿠키 전송을 허용하고, 응답 data.token을 data.headerName에 해당하는 요청 헤더에 넣어 인증 POST 요청을 전송하세요.")
    public ResponseEntity<ApiResponse<AuthResDTO.Csrf>> csrf(CsrfToken csrfToken) {
        AuthResDTO.Csrf data = new AuthResDTO.Csrf(csrfToken.getToken(), csrfToken.getHeaderName());
        return ResponseEntity.status(AuthSuccessCode.CSRF_OK.getStatus())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(AuthSuccessCode.CSRF_OK, data));
    }
}

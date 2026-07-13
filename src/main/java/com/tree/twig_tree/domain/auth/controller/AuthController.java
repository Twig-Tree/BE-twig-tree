package com.tree.twig_tree.domain.auth.controller;

import com.tree.twig_tree.domain.auth.dto.AuthReqDTO;
import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.code.AuthSuccessCode;
import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증(구글 로그인, 토큰 재발급, 로그아웃) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/google")
    @Operation(summary = "구글 로그인", description = "구글 ID 토큰을 검증하고 자체 JWT(access/refresh)를 발급합니다. 첫 로그인 시 자동 가입됩니다.")
    public ApiResponse<AuthResDTO.TokenPair> googleLogin(@RequestBody AuthReqDTO.GoogleLogin request) {
        return ApiResponse.onSuccess(AuthSuccessCode.LOGIN_OK, authService.googleLogin(request.idToken()));
    }
}

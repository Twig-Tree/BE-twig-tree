package com.tree.twig_tree.domain.auth.controller;

import com.tree.twig_tree.domain.auth.dto.AuthReqDTO;
import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.code.AuthSuccessCode;
import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
    public ApiResponse<AuthResDTO.TokenPair> googleLogin(@RequestBody @Valid AuthReqDTO.GoogleLogin request) {
        return ApiResponse.onSuccess(AuthSuccessCode.LOGIN_OK, authService.googleLogin(request.idToken()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "토큰 재발급",
            description = "리프레시 토큰으로 새 access/refresh 토큰을 발급합니다. 기존 리프레시 토큰은 폐기됩니다.<br>"
                    + "이미 폐기된 토큰이 다시 사용되면 탈취로 간주해 해당 회원의 모든 세션이 종료됩니다.")
    public ApiResponse<AuthResDTO.TokenPair> reissue(@RequestBody @Valid AuthReqDTO.Reissue request) {
        return ApiResponse.onSuccess(AuthSuccessCode.REFRESH_OK, authService.reissue(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "로그아웃",
            description = "리프레시 토큰을 폐기해 해당 세션을 종료합니다.<br>"
                    + "이미 발급된 액세스 토큰은 만료(최대 30분)까지 유효합니다.")
    public ApiResponse<Void> logout(@RequestBody @Valid AuthReqDTO.Logout request) {
        authService.logout(request.refreshToken());
        return ApiResponse.onSuccess(AuthSuccessCode.LOGOUT_OK, null);
    }
}

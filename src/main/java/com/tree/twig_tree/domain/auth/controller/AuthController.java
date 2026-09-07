package com.tree.twig_tree.domain.auth.controller;

import com.tree.twig_tree.domain.auth.dto.AuthReqDTO;
import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.exception.code.AuthSuccessCode;
import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.global.apiPayload.ApiResponse;
import com.tree.twig_tree.global.security.cookie.RefreshTokenCookieFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증(구글 로그인, 토큰 재발급, 로그아웃) API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieFactory refreshTokenCookieFactory;

    @PostMapping("/google")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf 응답의 data.token")
    @Operation(summary = "구글 로그인", description = "구글 ID 토큰을 검증합니다. Access Token과 회원 정보는 응답 본문으로, Refresh Token은 HttpOnly 쿠키로 전달합니다. 첫 로그인 시 자동 가입됩니다.")
    public ResponseEntity<ApiResponse<AuthResDTO.TokenResponse>> googleLogin(@RequestBody @Valid AuthReqDTO.GoogleLogin request) {
        AuthResDTO.TokenPair tokens = authService.googleLogin(request.idToken());
        AuthResDTO.TokenResponse data = new AuthResDTO.TokenResponse(tokens.accessToken(), tokens.member());
        return ResponseEntity.status(AuthSuccessCode.LOGIN_OK.getStatus())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(tokens.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(AuthSuccessCode.LOGIN_OK, data));
    }

    @PostMapping("/refresh")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf 응답의 data.token")
    @Operation(summary = "토큰 재발급",
            description = "요청 본문 없이 refresh_token 쿠키로 재발급합니다. Access Token과 회원 정보는 응답 본문으로, 새 Refresh Token은 HttpOnly 쿠키로 전달합니다.<br>"
                    + "기존 Refresh Token은 동시 요청 유예 기간 후 무효화되며, 이후 재사용 시 해당 회원의 모든 Refresh Token을 무효화합니다.")
    public ResponseEntity<ApiResponse<AuthResDTO.TokenResponse>> reissue(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new AuthException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }
        AuthResDTO.TokenPair tokens = authService.reissue(refreshToken);
        AuthResDTO.TokenResponse data = new AuthResDTO.TokenResponse(tokens.accessToken(), tokens.member());
        return ResponseEntity.status(AuthSuccessCode.REFRESH_OK.getStatus())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.create(tokens.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(AuthSuccessCode.REFRESH_OK, data));
    }

    @PostMapping("/logout")
    @Parameter(name = "X-XSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "GET /auth/csrf 응답의 data.token")
    @Operation(summary = "로그아웃",
            description = "요청 본문 없이 refresh_token 쿠키의 토큰을 폐기하고 쿠키를 삭제합니다. 쿠키가 없거나 토큰이 만료된 경우에도 성공합니다.<br>"
                    + "이미 발급된 액세스 토큰은 만료(최대 30분)까지 유효합니다.")
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshTokenCookieFactory.COOKIE_NAME, required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }
        return ResponseEntity.status(AuthSuccessCode.LOGOUT_OK.getStatus())
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookieFactory.clear().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(AuthSuccessCode.LOGOUT_OK, null));
    }
}

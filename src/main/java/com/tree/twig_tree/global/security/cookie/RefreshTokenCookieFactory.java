package com.tree.twig_tree.global.security.cookie;

import com.tree.twig_tree.global.security.jwt.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieFactory {

    public static final String COOKIE_NAME = "refresh_token";
    private static final String COOKIE_PATH = "/auth";

    private final AuthCookieProperties cookieProperties;
    private final JwtProperties jwtProperties;

    public ResponseCookie create(String refreshToken) {
        Assert.hasText(refreshToken, "Refresh token must not be blank");
        return build(refreshToken, Duration.ofMillis(jwtProperties.refreshTokenTtl()));
    }

    public ResponseCookie clear() {
        return build("", Duration.ZERO);
    }

    private ResponseCookie build(String value, Duration maxAge) {
        // Domain을 생략해 API 호스트에만 쿠키를 발급하며, 삭제 시에도 동일한 경로를 사용한다.
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite("Lax")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}

package com.tree.twig_tree.domain.auth.service;

import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.global.security.google.GoogleProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * 구글이 발급한 ID 토큰(RS256)을 구글 공개키(JWKS)로 검증한다
 * 검증 항목: 서명, 만료(exp), 발급자(iss), 대상(aud == 우리 client-id)
 * JWKS 다운로드/캐싱/키 로테이션은 NimbusJwtDecoder가 처리한다 (첫 검증 시점에 lazy fetch)
 */
@Component
public class GoogleIdTokenVerifier {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final JwtDecoder jwtDecoder;

    public GoogleIdTokenVerifier(GoogleProperties googleProperties) {
        this.jwtDecoder = buildDecoder(googleProperties);
    }

    public GoogleUserInfo verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            return new GoogleUserInfo(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture")
            );
        } catch (JwtException e) {
            // 서명 불일치, 만료, iss/aud 불일치 등 모든 검증 실패를 하나로 수렴
            throw new AuthException(AuthErrorCode.INVALID_GOOGLE_TOKEN);
        }
    }

    private JwtDecoder buildDecoder(GoogleProperties googleProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWKS_URI).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(GOOGLE_ISSUER),   // exp + iss 검증
                audienceValidator(googleProperties)                     // aud 검증 (직접 구현)
        ));
        return decoder;
    }

    /**
     * aud 클레임이 우리 client-id 목록 중 하나와 일치하는지 검증
     * 이 검증이 없으면 다른 서비스용으로 발급된 구글 토큰으로도 로그인이 가능해진다
     */
    private OAuth2TokenValidator<Jwt> audienceValidator(GoogleProperties googleProperties) {
        return jwt -> jwt.getAudience().stream().anyMatch(googleProperties.clientIds()::contains)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "aud가 우리 client-id와 일치하지 않습니다.", null));
    }

    public record GoogleUserInfo(
            String providerId,   // sub 클레임: 구글 계정의 불변 고유 식별자
            String email,
            String name,
            String profileImage
    ) {}
}

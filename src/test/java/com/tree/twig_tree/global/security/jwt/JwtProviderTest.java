package com.tree.twig_tree.global.security.jwt;

import com.tree.twig_tree.domain.member.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class JwtProviderTest {

    // 테스트 전용 키 (실제 secret 과 무관한 임의의 48바이트 키)
    private static final String SECRET =
            Base64.getEncoder().encodeToString("test-secret-key-for-jwt-provider-unit-test-48bytes!!".getBytes());

    private JwtProvider provider(long accessTtl) {
        return new JwtProvider(new JwtProperties(SECRET, accessTtl, 1_000_000L));
    }

    @Test
    void 발급한_액세슨_토큰을_파싱하면_memberId와_role이_나온다() {
        JwtProvider provider = provider(60_000L);
        String token = provider.createAccessToken(42L, Role.ROLE_USER);

        // token 에서 claim 추출
        Claims claims = provider.parseClaims(token);

        assertThat(provider.getMemberId(claims)).isEqualTo(42L);
        assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    void 액세스_토큰은_typ가_ACCESS다() {
        JwtProvider provider = provider(60_000L);
        Claims claims = provider.parseClaims(provider.createAccessToken(42L, Role.ROLE_USER));

        assertThat(provider.isTokenType(claims, TokenType.ACCESS)).isTrue();
        assertThat(provider.isTokenType(claims, TokenType.REFRESH)).isFalse();
    }

    @Test
    void 리프레시_토큰은_typ가_REFRESH이고_role이_없다() {
        JwtProvider provider = provider(60_000L);
        Claims claims = provider.parseClaims(provider.createRefreshToken(42L));

        assertThat(provider.isTokenType(claims, TokenType.REFRESH)).isTrue();
        // 인증에 쓰이면 안 되는 토큰이므로 access 로는 판정되지 않아야 한다
        assertThat(provider.isTokenType(claims, TokenType.ACCESS)).isFalse();
        assertThat(claims.get("role", String.class)).isNull();
    }

    @Test
    void 만료된_토큰은_ExpiredJwtException을_던진다() {
        // clock skew(60초)보다 확실히 과거로 만료시키기 위해 TTL 을 크게 음수로 설정
        JwtProvider provider = provider(-120_000L);
        String token = provider.createAccessToken(42L, Role.ROLE_USER);

        assertThatThrownBy(() -> provider.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void 서명이_변조된_토큰은_JwtException을_던진다() {
        JwtProvider provider = provider(60_000L);
        String token = provider.createAccessToken(42L, Role.ROLE_USER);
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThatThrownBy(() -> provider.parseClaims(tampered))
                .isInstanceOf(JwtException.class);
    }

}

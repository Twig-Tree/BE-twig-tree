package com.tree.twig_tree.global.security.jwt;

import com.tree.twig_tree.domain.member.entity.enums.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtProvider {

    private static final long CLOCK_SKEW_SECONDS = 60;

    private final JwtProperties jwtProperties;
    private final SecretKey key;

    public JwtProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;

        // base64 secret 문자열 -> HMAC-SHA 서명키
        this.key = Keys.hmacShaKeyFor(
                Decoders.BASE64.decode(jwtProperties.secret())
        );
    }

    public String createAccessToken(Long memberId, Role role) {
        return buildToken(memberId, jwtProperties.accessTokenTtl())
                .claim("role", role.name())
                .compact();
    }

    public String createRefreshToken(Long memberId) {
        return buildToken(memberId, jwtProperties.refreshTokenTtl())
                .compact();
    }

    private JwtBuilder buildToken(Long memberId, long ttlMillis) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(memberId))
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key);
    }

    // jwt token 으로부터 claim 추출
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .clockSkewSeconds(CLOCK_SKEW_SECONDS)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // claim 으로부터 member id 추출
    public Long getMemberId(Claims claims) {
        return Long.parseLong(claims.getSubject());
    }
}

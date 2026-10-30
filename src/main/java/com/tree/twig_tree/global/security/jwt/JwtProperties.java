package com.tree.twig_tree.global.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix="jwt")
public record JwtProperties(
        String secret,
        long accessTokenTtl,
        long refreshTokenTtl
) {
}

package com.tree.twig_tree.global.security.jwt;

/**
 * 자체 발급 JWT 의 용도 구분값.
 * payload 의 typ 클레임으로 실려
 * access 토큰이 재발급에 쓰이거나
 * 수명이 긴 refresh 토큰이 보호된 API 인증에 쓰이는 것을 막는다.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}

package com.tree.twig_tree.global.security;

import com.tree.twig_tree.global.security.handler.JwtAccessDeniedHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AuthOriginFilter extends OncePerRequestFilter {

    private static final Set<String> COOKIE_AUTH_PATHS = Set.of(
            "/auth/google", "/auth/refresh", "/auth/logout");

    private final CorsProperties corsProperties;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !COOKIE_AUTH_PATHS.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String origin = request.getHeader("Origin");
        if (origin == null || !corsProperties.allowedOrigins().contains(origin)) {
            accessDeniedHandler.handle(request, response, new AccessDeniedException("Untrusted auth origin"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}

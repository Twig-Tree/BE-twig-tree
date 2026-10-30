package com.tree.twig_tree.global.security;

import com.tree.twig_tree.global.security.handler.JwtAccessDeniedHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

@Component
@RequiredArgsConstructor
public class AuthOriginFilter extends OncePerRequestFilter {

    // getRequestURI() 를 문자열로 직접 비교하면 matrix 파라미터(/auth/refresh;a=b)나 인코딩 변형에서
    // 이 필터만 조용히 건너뛴다. 지금은 StrictHttpFirewall 이 그런 요청을 먼저 400 으로 끊어 실제로
    // 뚫리지는 않지만, 방화벽 설정을 완화하는 순간 드러나지 않는 구멍이 된다.
    // SecurityConfig 의 인가 규칙과 같은 PathPattern 매칭을 써서 두 판정이 어긋나지 않게 한다.
    private static final RequestMatcher COOKIE_AUTH_REQUESTS = new OrRequestMatcher(
            pathPattern(HttpMethod.POST, "/auth/google"),
            pathPattern(HttpMethod.POST, "/auth/refresh"),
            pathPattern(HttpMethod.POST, "/auth/logout"));

    private final CorsProperties corsProperties;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !COOKIE_AUTH_REQUESTS.matches(request);
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

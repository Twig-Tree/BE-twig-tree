package com.tree.twig_tree.global.security.jwt;

import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAuthenticationFilterTest {

    // 테스트 전용 키 (실제 secret 과 무관한 임의의 48바이트 키)
    private static final String SECRET =
            Base64.getEncoder().encodeToString("test-secret-key-for-jwt-provider-unit-test-48bytes!!".getBytes());

    private final JwtProvider jwtProvider = new JwtProvider(new JwtProperties(SECRET, 60_000L, 1_000_000L));
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtProvider);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWith(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    void 액세스_토큰이면_SecurityContext에_인증이_등록된다() throws Exception {
        MockHttpServletRequest request = requestWith(jwtProvider.createAccessToken(42L, Role.ROLE_USER));
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isEqualTo(42L);
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isNull();
    }

    @Test
    void 리프레시_토큰으로는_인증되지_않는다() throws Exception {
        MockHttpServletRequest request = requestWith(jwtProvider.createRefreshToken(42L));
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE))
                .isEqualTo(AuthErrorCode.INVALID_ACCESS_TOKEN);
    }

    @Test
    void 토큰이_없으면_인증도_에러도_없이_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        FilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE)).isNull();
    }

}

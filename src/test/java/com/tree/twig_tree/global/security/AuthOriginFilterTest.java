package com.tree.twig_tree.global.security;

import com.tree.twig_tree.global.security.handler.JwtAccessDeniedHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 필터 단독 동작을 본다.
 * 통합 경로에서는 StrictHttpFirewall 이 이상한 URI 를 먼저 400 으로 끊어버려서
 * 필터가 그 경로를 실제로 커버하는지 구분되지 않는다. 여기서는 방화벽 없이 직접 호출해
 * 판정 책임이 필터 자신에게 있는지 확인한다.
 */
class AuthOriginFilterTest {

    private static final String ALLOWED_ORIGIN = "https://app.twig-tree.com";

    private final AuthOriginFilter filter = new AuthOriginFilter(
            new CorsProperties(List.of(ALLOWED_ORIGIN)),
            new JwtAccessDeniedHandler(new ObjectMapper()));

    @ParameterizedTest
    @ValueSource(strings = {"/auth/google", "/auth/refresh", "/auth/logout"})
    void 허용된_Origin_은_통과한다(String path) throws Exception {
        MockFilterChain chain = doFilter(path, ALLOWED_ORIGIN);

        assertThat(chain.getRequest()).isNotNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/google", "/auth/refresh", "/auth/logout"})
    void Origin_이_없으면_거부한다(String path) throws Exception {
        MockFilterChain chain = doFilter(path, null);

        assertThat(chain.getRequest()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "https://app.twig-tree.com.evil.example", "null"})
    void 허용되지_않은_Origin_은_거부한다(String origin) throws Exception {
        MockFilterChain chain = doFilter("/auth/refresh", origin);

        assertThat(chain.getRequest()).isNull();
    }

    /*
     * 경로를 문자열로 비교하던 시절에는 아래 변형들이 필터를 통째로 건너뛰었다.
     * Spring MVC 는 matrix 파라미터를 떼고 매핑하므로 컨트롤러에는 그대로 도달한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "/auth/refresh;a=b",
            "/auth/logout;jsessionid=abc123",
            "/auth/google;a=b"
    })
    void matrix_파라미터가_붙어도_Origin_검증을_건너뛰지_않는다(String path) throws Exception {
        MockFilterChain chain = doFilter(path, null);

        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void 인증_경로가_아니면_검사하지_않는다() throws Exception {
        MockFilterChain chain = doFilter("/trees", null);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void POST_가_아니면_검사하지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/auth/refresh");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private MockFilterChain doFilter(String uri, String origin) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        return chain;
    }
}

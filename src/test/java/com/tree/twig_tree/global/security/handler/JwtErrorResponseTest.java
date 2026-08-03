package com.tree.twig_tree.global.security.handler;

import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import com.tree.twig_tree.global.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인증/인가 실패 응답이 공통 ApiResponse 형식과 UTF-8 로 나가는지 검증한다.
 */
class JwtErrorResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 인증_실패_응답은_UTF8_JSON이다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationEntryPoint(objectMapper)
                .commence(request, response, new BadCredentialsException("test"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        // 한글 메시지가 깨지지 않고 그대로 실려야 한다
        assertThat(response.getContentAsString()).contains("\"isSuccess\":false");
    }

    @Test
    void 필터가_남긴_에러코드가_응답에_반영된다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE, AuthErrorCode.EXPIRED_ACCESS_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAuthenticationEntryPoint(objectMapper)
                .commence(request, response, new BadCredentialsException("test"));

        assertThat(response.getContentAsString())
                .contains(AuthErrorCode.EXPIRED_ACCESS_TOKEN.getCode())
                .contains(AuthErrorCode.EXPIRED_ACCESS_TOKEN.getMessage());
    }

    @Test
    void 인가_실패_응답도_같은_형식이다() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        new JwtAccessDeniedHandler(objectMapper)
                .handle(new MockHttpServletRequest(), response, new AccessDeniedException("test"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8");
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    }

}

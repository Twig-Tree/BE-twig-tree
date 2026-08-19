package com.tree.twig_tree.global.security;

import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.global.security.handler.JwtAccessDeniedHandler;
import com.tree.twig_tree.global.security.handler.JwtAuthenticationEntryPoint;
import com.tree.twig_tree.global.security.jwt.JwtAuthenticationFilter;
import com.tree.twig_tree.global.security.jwt.JwtProperties;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import jakarta.servlet.DispatcherType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.Base64;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig 의 인가 규칙을 고정한다.
 * <p>
 * 개발 편의를 위해 {@code anyRequest().permitAll()} 로 열어둔 시기가 있었고, 그 상태에서는
 * 인증이 필요한 요청이 컨트롤러까지 도달해 500 으로 드러났다. 설정 한 줄로 되돌아갈 수 있는
 * 변경이므로 규칙 자체를 테스트로 잡아둔다.
 * <p>
 * 컨트롤러나 DB 는 띄우지 않는다. 보안 필터가 MVC 앞단에서 판정하므로, 통과한 요청이
 * 핸들러를 찾지 못해 404 가 되더라도 "401 이 아니다" 로 충분히 구분된다.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        SecurityConfigAuthorizationTest.TestConfig.class,
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        JwtProvider.class,
        JwtAuthenticationEntryPoint.class,
        JwtAccessDeniedHandler.class,
})
class SecurityConfigAuthorizationTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("test-secret-key-for-security-config-test-48bytes!!".getBytes());

    @EnableWebMvc
    @Configuration
    static class TestConfig {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(SECRET, 1_800_000L, 1_209_600_000L);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Autowired
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/members/me",
            "/trees",
            "/workspaces",
            "/folders",
            "/nodes/1/memos",
            "/trees/1/nodes",
            "/tree-request",
    })
    void 토큰이_없으면_401이다(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인증_실패_응답은_공통_ApiResponse_형식이다() throws Exception {
        mockMvc.perform(get("/trees"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401-1"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/google", "/auth/refresh", "/auth/logout"})
    void 로그인_경로는_토큰_없이_열려_있다(String path) throws Exception {
        // 토큰을 받기 위한 경로이므로 인증을 요구하면 로그인 자체가 불가능해진다
        mockMvc.perform(post(path))
                .andExpect(status().isNotFound()); // 인가는 통과하고 핸들러가 없어 404
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/v3/api-docs"})
    void 스웨거는_토큰_없이_열려_있다(String path) throws Exception {
        mockMvc.perform(get(path))
                .andExpect(status().isNotFound());
    }

    @Test
    void 유효한_액세스_토큰이면_인가를_통과한다() throws Exception {
        String token = jwtProvider.createAccessToken(1L, Role.ROLE_USER);

        mockMvc.perform(get("/trees").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    /**
     * 인가 필터는 기본적으로 모든 디스패치 타입에 적용된다. 오류 페이지로의 내부 디스패치까지
     * 인증을 요구하면 예외의 실제 원인 대신 401 이 나가 원인 추적이 불가능해진다.
     */
    @Test
    void 오류_페이지로의_내부_디스패치는_인가_대상이_아니다() throws Exception {
        mockMvc.perform(get("/error").with(request -> {
                    request.setDispatcherType(DispatcherType.ERROR);
                    return request;
                }))
                .andExpect(status().isNotFound()); // 401 이 아니라 핸들러 없음으로 통과
    }

    @Test
    void 리프레시_토큰으로는_인증되지_않는다() throws Exception {
        String refreshToken = jwtProvider.createRefreshToken(1L).token();

        mockMvc.perform(get("/trees").header("Authorization", "Bearer " + refreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401-3"));
    }
}

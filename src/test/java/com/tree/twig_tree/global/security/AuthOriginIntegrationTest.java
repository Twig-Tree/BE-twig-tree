package com.tree.twig_tree.global.security;

import com.tree.twig_tree.domain.auth.controller.AuthController;
import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.domain.member.dto.MemberResDTO;
import com.tree.twig_tree.domain.member.entity.enums.Role;
import com.tree.twig_tree.global.apiPayload.handler.GeneralExceptionAdvice;
import com.tree.twig_tree.global.security.cookie.RefreshTokenCookieFactory;
import com.tree.twig_tree.global.security.handler.JwtAccessDeniedHandler;
import com.tree.twig_tree.global.security.handler.JwtAuthenticationEntryPoint;
import com.tree.twig_tree.global.security.jwt.JwtAuthenticationFilter;
import com.tree.twig_tree.global.security.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        SecurityConfigAuthorizationTest.TestConfig.class, AuthOriginIntegrationTest.TestConfig.class,
        SecurityConfig.class, AuthOriginFilter.class, JwtAuthenticationFilter.class, JwtProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        AuthController.class, RefreshTokenCookieFactory.class,
        GeneralExceptionAdvice.class, AuthOriginIntegrationTest.BusinessController.class
})
class AuthOriginIntegrationTest {

    private static final String APP_ORIGIN = "https://app.twig-tree.com";

    @RestController
    static class BusinessController {
        @PostMapping("/trees")
        String createTree() {
            return "ok";
        }
    }

    @Configuration
    static class TestConfig {
        @Bean
        AuthService authService() {
            return mock(AuthService.class);
        }
    }

    @Autowired
    private AuthService authService;
    @Autowired
    private JwtProvider jwtProvider;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        reset(authService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void allowedOriginCanLoginRefreshAndLogoutWithoutCsrfToken() throws Exception {
        MemberResDTO.Me member = new MemberResDTO.Me(1L, "user@example.com", "사용자", null);
        when(authService.googleLogin("google-token"))
                .thenReturn(new AuthResDTO.TokenPair("access-1", "refresh-1", member));
        when(authService.reissue("refresh-1"))
                .thenReturn(new AuthResDTO.TokenPair("access-2", "refresh-2", member));

        var login = mockMvc.perform(post("/auth/google").secure(true)
                        .header("Origin", APP_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-1"))
                .andReturn();
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertThat(refresh).isNotNull();
        assertThat(login.getResponse().getCookie("__Host-csrf_token")).isNull();

        var reissue = mockMvc.perform(post("/auth/refresh").secure(true)
                        .header("Origin", APP_ORIGIN).cookie(refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-2"))
                .andReturn();
        Cookie rotated = reissue.getResponse().getCookie("refresh_token");
        assertThat(rotated).isNotNull();

        mockMvc.perform(post("/auth/logout").secure(true)
                        .header("Origin", APP_ORIGIN).cookie(rotated))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("refresh_token", 0));
        verify(authService).logout("refresh-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/google", "/auth/refresh", "/auth/logout"})
    void missingOriginIsRejectedEvenWithBearer(String path) throws Exception {
        mockMvc.perform(post(path).secure(true)
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L, Role.ROLE_USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403-1"));
        verifyNoInteractions(authService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://untrusted.example", "https://app.twig-tree.com.evil.example", "null"})
    void untrustedOriginIsRejectedWithRefreshCookie(String origin) throws Exception {
        mockMvc.perform(post("/auth/refresh").secure(true)
                        .header("Origin", origin).cookie(new Cookie("refresh_token", "refresh-1")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(authService);
    }

    @Test
    void oldCsrfEndpointIsGone() throws Exception {
        mockMvc.perform(get("/auth/csrf").header("Origin", APP_ORIGIN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerOnlyBusinessPostNeedsNoOrigin() throws Exception {
        mockMvc.perform(post("/trees")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L, Role.ROLE_USER)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/trees")).andExpect(status().isUnauthorized());
    }
}

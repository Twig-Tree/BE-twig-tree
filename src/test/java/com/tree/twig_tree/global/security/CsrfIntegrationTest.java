package com.tree.twig_tree.global.security;

import com.tree.twig_tree.domain.auth.controller.AuthController;
import com.tree.twig_tree.domain.auth.controller.CsrfController;
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
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = {
        SecurityConfigAuthorizationTest.TestConfig.class, CsrfIntegrationTest.TestConfig.class,
        SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class,
        JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class,
        AuthController.class, CsrfController.class, RefreshTokenCookieFactory.class,
        GeneralExceptionAdvice.class, CsrfIntegrationTest.BusinessController.class
})
class CsrfIntegrationTest {

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
    @Autowired
    private ObjectMapper objectMapper;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        reset(authService);
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private record CsrfPair(Cookie cookie, String token) {}

    private CsrfPair issueCsrf() throws Exception {
        var result = mockMvc.perform(get("/auth/csrf").secure(true)
                        .header("Origin", "https://app.twig-tree.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200-4"))
                .andExpect(jsonPath("$.data.headerName").value("X-XSRF-TOKEN"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("__Host-csrf_token");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getDomain()).isNull();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
        assertThat(result.getRequest().getSession(false)).isNull();
        String token = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
        assertThat(token).isNotBlank().isNotEqualTo(cookie.getValue());
        return new CsrfPair(cookie, token);
    }

    @Test
    void realCsrfCookieAndJsonTokenAllowLoginRefreshAndLogout() throws Exception {
        CsrfPair csrf = issueCsrf();
        MemberResDTO.Me member = new MemberResDTO.Me(1L, "user@example.com", "사용자", null);
        when(authService.googleLogin("google-token"))
                .thenReturn(new AuthResDTO.TokenPair("access-1", "refresh-1", member));
        when(authService.reissue("refresh-1"))
                .thenReturn(new AuthResDTO.TokenPair("access-2", "refresh-2", member));

        var login = mockMvc.perform(post("/auth/google").secure(true)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.token())
                        .header("Origin", "https://app.twig-tree.com")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idToken\":\"google-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-1"))
                .andReturn();
        Cookie refresh = login.getResponse().getCookie("refresh_token");
        assertThat(refresh).isNotNull();
        var reissue = mockMvc.perform(post("/auth/refresh").secure(true)
                        .cookie(csrf.cookie(), refresh).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-2"))
                .andReturn();
        Cookie rotated = reissue.getResponse().getCookie("refresh_token");
        assertThat(rotated).isNotNull();
        mockMvc.perform(post("/auth/logout").secure(true)
                        .cookie(csrf.cookie(), rotated).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isOk()).andExpect(cookie().maxAge("refresh_token", 0));
        verify(authService).logout("refresh-2");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/google", "/auth/refresh", "/auth/logout"})
    void missingTokenIsRejectedEvenWithAllowedOriginAndBearer(String path) throws Exception {
        mockMvc.perform(post(path).secure(true)
                        .header("Origin", "https://app.twig-tree.com")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L, Role.ROLE_USER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH403-1"))
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.twig-tree.com"));
        verifyNoInteractions(authService);
    }

    @Test
    void csrfCookieAloneCannotAuthorizeRequest() throws Exception {
        CsrfPair csrf = issueCsrf();
        mockMvc.perform(post("/auth/logout").cookie(csrf.cookie()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403-1"));
        verifyNoInteractions(authService);
    }

    @Test
    void csrfHeaderWithoutCookieIsRejected() throws Exception {
        CsrfPair csrf = issueCsrf();
        mockMvc.perform(post("/auth/logout").header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403-1"));
        verifyNoInteractions(authService);
    }

    @Test
    void tokenFromAnotherBrowserIsRejected() throws Exception {
        CsrfPair first = issueCsrf();
        CsrfPair second = issueCsrf();
        mockMvc.perform(post("/auth/logout").cookie(first.cookie()).header("X-XSRF-TOKEN", second.token()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403-1"));
        verifyNoInteractions(authService);
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        CsrfPair csrf = issueCsrf();
        mockMvc.perform(post("/auth/logout").cookie(csrf.cookie()).header("X-XSRF-TOKEN", "invalid"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH403-1"));
        verifyNoInteractions(authService);
    }

    @Test
    void untrustedOriginCannotReadCsrfToken() throws Exception {
        mockMvc.perform(get("/auth/csrf").header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void bearerOnlyBusinessPostDoesNotRequireCsrf() throws Exception {
        mockMvc.perform(post("/trees")
                        .header("Authorization", "Bearer " + jwtProvider.createAccessToken(1L, Role.ROLE_USER)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/trees")).andExpect(status().isUnauthorized());
    }
}

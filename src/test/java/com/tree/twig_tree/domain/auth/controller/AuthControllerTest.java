package com.tree.twig_tree.domain.auth.controller;

import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.domain.auth.dto.AuthResDTO;
import com.tree.twig_tree.domain.member.dto.MemberResDTO;
import com.tree.twig_tree.global.security.cookie.AuthCookieProperties;
import com.tree.twig_tree.global.security.cookie.RefreshTokenCookieFactory;
import com.tree.twig_tree.global.security.jwt.JwtProperties;
import com.tree.twig_tree.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.servlet.http.Cookie;
import com.tree.twig_tree.domain.auth.exception.AuthException;
import com.tree.twig_tree.domain.auth.exception.code.AuthErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final RefreshTokenCookieFactory cookieFactory = new RefreshTokenCookieFactory(
            new AuthCookieProperties(true), new JwtProperties("unused", 1800000, 1209600000));
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(authService, cookieFactory))
            .setControllerAdvice(new GeneralExceptionAdvice())
            .build();

    private void expectBadRequest(String body) throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.data.idToken").exists())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(authService);
    }

    @Test
    void loginReturnsAccessTokenInBodyAndRefreshTokenOnlyInCookie() throws Exception {
        MemberResDTO.Me member = new MemberResDTO.Me(1L, "user@example.com", "사용자", null);
        when(authService.googleLogin("google-id-token"))
                .thenReturn(new AuthResDTO.TokenPair("access-value", "refresh-value", member));

        var result = mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"google-id-token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("AUTH200-1"))
                .andExpect(jsonPath("$.data.accessToken").value("access-value"))
                .andExpect(jsonPath("$.data.member.memberId").value(1))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=refresh-value;")
                .contains("Path=/auth", "Max-Age=1209600", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("refresh-value");
    }

    @Test
    void idToken_필드가_없으면_400이다() throws Exception {
        expectBadRequest("{}");
    }

    @Test
    void idToken이_null이면_400이다() throws Exception {
        expectBadRequest("{\"idToken\": null}");
    }

    @Test
    void idToken이_공백이면_400이다() throws Exception {
        expectBadRequest("{\"idToken\": \"   \"}");
    }

    @Test
    void refreshUsesCookieWithoutBodyAndRotatesCookie() throws Exception {
        when(authService.reissue("old-refresh"))
                .thenReturn(new AuthResDTO.TokenPair("new-access", "new-refresh",
                        new MemberResDTO.Me(1L, "user@example.com", "사용자", null)));

        var result = mockMvc.perform(post("/auth/refresh")
                        .cookie(new Cookie("refresh_token", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200-2"))
                .andExpect(jsonPath("$.data.accessToken").value("new-access"))
                .andExpect(jsonPath("$.data.member.memberId").value(1))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        verify(authService).reissue("old-refresh");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=new-refresh;")
                .contains("Path=/auth", "Max-Age=1209600", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("new-refresh", "old-refresh");
    }

    @Test
    void refreshRejectsMissingCookieEvenWhenBodyContainsToken() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"body-token\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401-4"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(authService);
    }

    @Test
    void refreshRejectsEmptyCookie() throws Exception {
        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH401-4"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
        verifyNoInteractions(authService);
    }

    @Test
    void logoutRevokesCookieTokenAndClearsCookieWithoutBody() throws Exception {
        var result = mockMvc.perform(post("/auth/logout")
                        .cookie(new Cookie("refresh_token", "logout-refresh")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200-3"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();

        verify(authService).logout("logout-refresh");
        assertThat(result.getResponse().getHeaders(HttpHeaders.SET_COOKIE)).hasSize(1);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=;")
                .contains("Path=/auth", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Lax")
                .doesNotContain("Domain=");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("logout-refresh");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void logoutWithoutUsableCookieStillClearsCookie(String token) throws Exception {
        var request = post("/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"ignored-body-token\"}");
        if (token != null) {
            request.cookie(new Cookie("refresh_token", token));
        }

        var result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AUTH200-3"))
                .andReturn();

        verifyNoInteractions(authService);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .startsWith("refresh_token=;").contains("Path=/auth", "Max-Age=0");
    }

    @Test
    void logoutStoreFailureDoesNotReportSuccessOrClearCookie() throws Exception {
        doThrow(new AuthException(AuthErrorCode.TOKEN_STORE_UNAVAILABLE))
                .when(authService).logout("logout-refresh");

        mockMvc.perform(post("/auth/logout").cookie(new Cookie("refresh_token", "logout-refresh")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH503-1"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @ParameterizedTest
    @EnumSource(value = AuthErrorCode.class, names = {
            "INVALID_REFRESH_TOKEN", "EXPIRED_REFRESH_TOKEN", "TOKEN_STORE_UNAVAILABLE"
    })
    void refreshPreservesServiceErrorWithoutIssuingCookie(AuthErrorCode errorCode) throws Exception {
        when(authService.reissue("rejected-refresh")).thenThrow(new AuthException(errorCode));

        mockMvc.perform(post("/auth/refresh").cookie(new Cookie("refresh_token", "rejected-refresh")))
                .andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value(errorCode.getCode()))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

}

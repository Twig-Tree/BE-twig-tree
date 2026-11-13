package com.tree.twig_tree.domain.member.controller;

import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.domain.member.exception.MemberException;
import com.tree.twig_tree.domain.member.exception.code.MemberErrorCode;
import com.tree.twig_tree.domain.member.service.MemberService;
import com.tree.twig_tree.global.apiPayload.handler.GeneralExceptionAdvice;
import com.tree.twig_tree.global.security.cookie.AuthCookieProperties;
import com.tree.twig_tree.global.security.cookie.RefreshTokenCookieFactory;
import com.tree.twig_tree.global.security.jwt.JwtProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MemberControllerWithdrawTest {

    private static final Long MEMBER_ID = 7L;

    private AuthService authService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        RefreshTokenCookieFactory cookieFactory = new RefreshTokenCookieFactory(
            new AuthCookieProperties(true), new JwtProperties("unused", 1800000, 1209600000));
        mockMvc = MockMvcBuilders
            .standaloneSetup(new MemberController(mock(MemberService.class), authService, cookieFactory))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GeneralExceptionAdvice())
            .build();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(MEMBER_ID, null, List.of()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("탈퇴하면 200 과 함께 refresh_token 쿠키를 지우고 캐시하지 않는다")
    void withdraws() throws Exception {
        mockMvc.perform(delete("/members/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.code").value("MEMBER200-2"))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, allOf(
                containsString(RefreshTokenCookieFactory.COOKIE_NAME + "="),
                containsString("Max-Age=0"),
                containsString("Path=/auth"))))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        verify(authService).withdraw(MEMBER_ID);
    }

    @Test
    @DisplayName("이미 탈퇴한 회원이면 404 MEMBER404-1 이다")
    void rejectsWithdrawnMember() throws Exception {
        doThrow(new MemberException(MemberErrorCode.MEMBER_NOT_FOUND)).when(authService).withdraw(MEMBER_ID);

        mockMvc.perform(delete("/members/me"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("MEMBER404-1"));
    }
}

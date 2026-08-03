package com.tree.twig_tree.domain.auth.controller;

import com.tree.twig_tree.domain.auth.service.AuthService;
import com.tree.twig_tree.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerTest {

    /*
     * 의존성을 null 로 넣은 AuthService 를 주입한다.
     * 검증 단계에서 걸러지면 서비스는 호출되지 않으므로 문제가 없고,
     * 만약 검증이 동작하지 않아 서비스까지 흘러가면 NPE(500)로 드러나
     * 테스트가 실패하게 된다.
     */
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AuthController(new AuthService(null, null, null)))
            .setControllerAdvice(new GeneralExceptionAdvice())
            .build();

    private void expectBadRequest(String body) throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400-1"))
                .andExpect(jsonPath("$.data.idToken").exists());
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

}

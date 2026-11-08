package com.tree.twig_tree.domain.lecture.controller;

import com.tree.twig_tree.domain.lecture.dto.LectureGenResDTO;
import com.tree.twig_tree.domain.lecture.dto.LectureReqDTO;
import com.tree.twig_tree.domain.lecture.exception.LectureException;
import com.tree.twig_tree.domain.lecture.exception.code.LectureErrorCode;
import com.tree.twig_tree.domain.lecture.service.LectureGenerationService;
import com.tree.twig_tree.global.apiPayload.handler.GeneralExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LectureControllerTest {

    private LectureGenerationService lectureGenerationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lectureGenerationService = mock(LectureGenerationService.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new LectureController(lectureGenerationService))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GeneralExceptionAdvice())
            .build();
    }

    private String body(String request, String outline, String extra) {
        return """
            { "treeId": 10, "request": "%s", "treeOutline": "%s", "scenarioSchemaVersion": 1%s }
            """.formatted(request, outline, extra);
    }

    @Test
    @DisplayName("생성에 성공하면 200 과 모델 응답 본문을 돌려준다")
    void generates() throws Exception {
        when(lectureGenerationService.generate(any(), any(LectureReqDTO.class)))
            .thenReturn(LectureGenResDTO.builder()
                .responseText("{\"scenarioSchemaVersion\":1}")
                .model("gpt-4o-mini-2024-07-18")
                .promptVersion("lecture-v1")
                .usage(new LectureGenResDTO.Usage(1200, 340))
                .attemptCount(1)
                .build());

        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("비교해서 설명해 줘.", "- [1] 척추동물", "")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.isSuccess").value(true))
            .andExpect(jsonPath("$.data.responseText").value("{\"scenarioSchemaVersion\":1}"))
            .andExpect(jsonPath("$.data.promptVersion").value("lecture-v1"))
            .andExpect(jsonPath("$.data.usage.inputTokens").value(1200))
            .andExpect(jsonPath("$.data.attemptCount").value(1));

        verify(lectureGenerationService).generate(any(), any(LectureReqDTO.class));
    }

    @Test
    @DisplayName("요청 문장이 비어 있으면 400 이고 서비스를 부르지 않는다")
    void rejectsBlankRequest() throws Exception {
        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("", "- [1] 척추동물", "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.isSuccess").value(false));

        verify(lectureGenerationService, never()).generate(any(), any());
    }

    @Test
    @DisplayName("트리 outline 이 비어 있으면 400 이다")
    void rejectsBlankOutline() throws Exception {
        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("비교해서 설명해 줘.", "", "")))
            .andExpect(status().isBadRequest());

        verify(lectureGenerationService, never()).generate(any(), any());
    }

    @Test
    @DisplayName("스키마 버전이 없으면 400 이다")
    void rejectsMissingSchemaVersion() throws Exception {
        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"treeId\":10,\"request\":\"설명해 줘.\",\"treeOutline\":\"- [1] 척추동물\"}"))
            .andExpect(status().isBadRequest());

        verify(lectureGenerationService, never()).generate(any(), any());
    }

    @Test
    @DisplayName("요청 문장이 상한을 넘으면 400 이다")
    void rejectsTooLongRequest() throws Exception {
        String tooLong = "가".repeat(LectureReqDTO.REQUEST_MAX_LENGTH + 1);

        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(tooLong, "- [1] 척추동물", "")))
            .andExpect(status().isBadRequest());

        verify(lectureGenerationService, never()).generate(any(), any());
    }

    @Test
    @DisplayName("repair 문제 수가 상한을 넘으면 400 이다")
    void rejectsTooManyRepairIssues() throws Exception {
        String issue = "{\"stage\":\"node\",\"path\":\"$.scenes[0]\",\"message\":\"문제\"}";
        String issues = String.join(",", java.util.Collections.nCopies(
            LectureReqDTO.MAX_REPAIR_ISSUES + 1, issue));
        String repair = ",\"repair\":{\"attempt\":1,\"previousResponseText\":\"{}\",\"issues\":["
            + issues + "],\"omittedIssueCount\":0}";

        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("설명해 줘.", "- [1] 척추동물", repair)))
            .andExpect(status().isBadRequest());

        verify(lectureGenerationService, never()).generate(any(), any());
    }

    @Test
    @DisplayName("서비스의 도메인 예외는 그 에러코드와 상태로 응답한다")
    void mapsDomainException() throws Exception {
        when(lectureGenerationService.generate(any(), any(LectureReqDTO.class)))
            .thenThrow(new LectureException(LectureErrorCode.SCHEMA_VERSION_MISMATCH));

        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("설명해 줘.", "- [1] 척추동물", "")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(LectureErrorCode.SCHEMA_VERSION_MISMATCH.getCode()));
    }

    @Test
    @DisplayName("응답이 잘린 경우는 502 로 응답한다")
    void mapsTruncatedResponse() throws Exception {
        when(lectureGenerationService.generate(any(), any(LectureReqDTO.class)))
            .thenThrow(new LectureException(LectureErrorCode.RESPONSE_TRUNCATED));

        mockMvc.perform(post("/lecture-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("설명해 줘.", "- [1] 척추동물", "")))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value(LectureErrorCode.RESPONSE_TRUNCATED.getCode()));
    }
}

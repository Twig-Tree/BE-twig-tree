package com.tree.twig_tree.domain.chat.controller;

import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.service.ChatService;
import com.tree.twig_tree.global.mock.MockResponseLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /tree-request 하나의 경로에 JSON 요청과 multipart 요청이 함께 매핑되므로,
 * Content-Type 에 따라 의도한 핸들러로 분기되는지 확인한다.
 */
class ChatControllerRoutingTest {

    private ChatService chatService;
    private MockResponseLoader mockResponseLoader;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        mockResponseLoader = mock(MockResponseLoader.class);
        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatController(chatService, mockResponseLoader))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .build();

        when(chatService.generateTree(any(), any(ChatReqDTO.class)))
            .thenReturn(TreeGenResDTO.builder().treeId(1L).nodes(List.of()).build());
        when(chatService.generateTree(any(), any(), any(), any()))
            .thenReturn(TreeGenResDTO.builder().treeId(2L).nodes(List.of()).build());
    }

    @Test
    @DisplayName("multipart 요청은 파일 핸들러로 간다")
    void multipartGoesToFileHandler() throws Exception {
        MockMultipartFile file =
            new MockMultipartFile("file", "note.md", "text/markdown", "본문".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tree-request")
                .file(file)
                .param("message", "3단계로 정리해줘")
                .param("provider", "OPENAI"))
            .andExpect(status().isCreated());

        verify(chatService).generateTree(any(), eq(LlmProvider.OPENAI), eq("3단계로 정리해줘"), any(MultipartFile.class));
        verify(chatService, never()).generateTree(any(), any(ChatReqDTO.class));
    }

    @Test
    @DisplayName("파일 없이 message 만 있는 multipart 요청도 파일 핸들러로 간다")
    void multipartWithoutFile() throws Exception {
        mockMvc.perform(multipart("/tree-request")
                .param("message", "트리 만들어줘")
                .param("provider", "OPENAI"))
            .andExpect(status().isCreated());

        verify(chatService).generateTree(any(), eq(LlmProvider.OPENAI), eq("트리 만들어줘"), any());
    }

    @Test
    @DisplayName("기존 JSON 요청은 그대로 JSON 핸들러로 간다")
    void jsonStillGoesToJsonHandler() throws Exception {
        mockMvc.perform(post("/tree-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"트리 만들어줘\",\"provider\":\"OPENAI\"}"))
            .andExpect(status().isCreated());

        verify(chatService).generateTree(any(), any(ChatReqDTO.class));
        verify(chatService, never()).generateTree(any(), any(), any(), any());
    }

    @Test
    @DisplayName("message 가 상한(500자)을 넘으면 서비스까지 가지 않고 400")
    void jsonMessageTooLong() throws Exception {
        String tooLong = "가".repeat(ChatReqDTO.MESSAGE_MAX_LENGTH + 1);

        mockMvc.perform(post("/tree-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + tooLong + "\",\"provider\":\"OPENAI\"}"))
            .andExpect(status().isBadRequest());

        // LLM 호출은 물론 서비스 진입 자체가 없어야 한다
        verify(chatService, never()).generateTree(any(), any(ChatReqDTO.class));
    }

    @Test
    @DisplayName("message 가 상한(500자)과 같으면 통과한다")
    void jsonMessageAtLimit() throws Exception {
        String atLimit = "가".repeat(ChatReqDTO.MESSAGE_MAX_LENGTH);

        mockMvc.perform(post("/tree-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + atLimit + "\",\"provider\":\"OPENAI\"}"))
            .andExpect(status().isCreated());

        verify(chatService).generateTree(any(), any(ChatReqDTO.class));
    }

    @Test
    @DisplayName("Content-Type 없는 mock 요청도 기존대로 동작한다")
    void mockRequestWithoutContentType() throws Exception {
        mockMvc.perform(post("/tree-request").param("mock", "small"))
            .andExpect(status().isCreated());

        verify(mockResponseLoader).load("mocks/chat/tree-small.json");
        verify(chatService, never()).generateTree(any(), any(ChatReqDTO.class));
        verify(chatService, never()).generateTree(any(), any(), any(), any());
    }
}

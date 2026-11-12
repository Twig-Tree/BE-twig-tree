package com.tree.twig_tree.domain.chat.controller;

import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.service.ChatService;
import com.tree.twig_tree.global.apiPayload.handler.GeneralExceptionAdvice;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /workspaces/{workspaceId}/tree-request 가 기존 /tree-request 와 같은 방식으로
 * Content-Type 에 따라 분기되고, 워크스페이스를 새로 만드는 경로와 섞이지 않는지 확인한다.
 */
class ChatControllerWorkspaceRoutingTest {

    private static final long WORKSPACE_ID = 7L;

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
            .setControllerAdvice(new GeneralExceptionAdvice())
            .build();

        when(chatService.generateTreeInWorkspace(any(), anyLong(), any(ChatReqDTO.class)))
            .thenReturn(TreeGenResDTO.builder().treeId(1L).workspaceId(WORKSPACE_ID).nodes(List.of()).build());
        when(chatService.generateTreeInWorkspace(any(), anyLong(), any(), any(), any()))
            .thenReturn(TreeGenResDTO.builder().treeId(2L).workspaceId(WORKSPACE_ID).nodes(List.of()).build());
    }

    @Test
    @DisplayName("JSON 요청은 워크스페이스 JSON 핸들러로 가고 경로의 workspaceId 를 넘긴다")
    void jsonGoesToWorkspaceHandler() throws Exception {
        mockMvc.perform(post("/workspaces/{workspaceId}/tree-request", WORKSPACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"트리 만들어줘\",\"provider\":\"OPENAI\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.workspaceId").value(WORKSPACE_ID));

        verify(chatService).generateTreeInWorkspace(any(), eq(WORKSPACE_ID), any(ChatReqDTO.class));
        // 워크스페이스를 새로 만드는 경로로는 가지 않아야 한다
        verify(chatService, never()).generateTree(any(), any(ChatReqDTO.class));
    }

    @Test
    @DisplayName("multipart 요청은 워크스페이스 파일 핸들러로 간다")
    void multipartGoesToWorkspaceFileHandler() throws Exception {
        MockMultipartFile file =
            new MockMultipartFile("file", "note.md", "text/markdown", "본문".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/workspaces/{workspaceId}/tree-request", WORKSPACE_ID)
                .file(file)
                .param("message", "3단계로 정리해줘")
                .param("provider", "OPENAI"))
            .andExpect(status().isCreated());

        verify(chatService).generateTreeInWorkspace(
            any(), eq(WORKSPACE_ID), eq(LlmProvider.OPENAI), eq("3단계로 정리해줘"), any(MultipartFile.class));
        verify(chatService, never()).generateTree(any(), any(), any(), any());
    }

    @Test
    @DisplayName("message 가 상한(500자)을 넘으면 서비스까지 가지 않고 400")
    void messageTooLong() throws Exception {
        String tooLong = "가".repeat(ChatReqDTO.MESSAGE_MAX_LENGTH + 1);

        mockMvc.perform(post("/workspaces/{workspaceId}/tree-request", WORKSPACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + tooLong + "\",\"provider\":\"OPENAI\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON400-1"));

        verify(chatService, never()).generateTreeInWorkspace(any(), anyLong(), any(ChatReqDTO.class));
    }

    @Test
    @DisplayName("mock 요청은 서비스를 호출하지 않고, 응답 workspaceId 를 경로 값으로 바꿔 반환한다")
    void mockRequestOverridesWorkspaceId() throws Exception {
        when(mockResponseLoader.load("mocks/chat/tree-small.json")).thenReturn(mockTree());

        mockMvc.perform(post("/workspaces/{workspaceId}/tree-request", WORKSPACE_ID).param("mock", "small"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.treeId").value(12))
            .andExpect(jsonPath("$.data.workspaceId").value(WORKSPACE_ID));

        verify(chatService, never()).generateTreeInWorkspace(any(), anyLong(), any(ChatReqDTO.class));
    }

    @Test
    @DisplayName("multipart mock 요청도 응답 workspaceId 를 경로 값으로 바꿔 반환한다")
    void multipartMockOverridesWorkspaceId() throws Exception {
        when(mockResponseLoader.load("mocks/chat/tree-small.json")).thenReturn(mockTree());

        mockMvc.perform(multipart("/workspaces/{workspaceId}/tree-request", WORKSPACE_ID)
                .param("mock", "small"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.workspaceId").value(WORKSPACE_ID));

        verify(chatService, never()).generateTreeInWorkspace(any(), anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("mock 시나리오 이름이 허용 문자 밖이면 400")
    void invalidMockScenario() throws Exception {
        mockMvc.perform(post("/workspaces/{workspaceId}/tree-request", WORKSPACE_ID)
                .param("mock", "../secret"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("CHAT400-12"));
    }

    /** 기존 mock 파일과 같은 모양: workspaceId 가 경로와 다른 값으로 들어 있다. */
    private ObjectNode mockTree() {
        ObjectNode node = JsonMapper.builder().build().createObjectNode();
        node.put("treeId", 12L);
        node.put("workspaceId", 12L);
        node.putArray("nodes");
        return node;
    }
}

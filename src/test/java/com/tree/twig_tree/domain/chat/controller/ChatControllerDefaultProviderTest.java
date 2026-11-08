package com.tree.twig_tree.domain.chat.controller;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.parser.PlainTextParser;
import com.tree.twig_tree.domain.chat.service.ChatService;
import com.tree.twig_tree.domain.chat.service.DocumentTextExtractor;
import com.tree.twig_tree.domain.chat.service.GeneratedTreeWriter;
import com.tree.twig_tree.domain.chat.service.TreeValidator;
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
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * provider 를 생략한 요청이 실제 {@link ChatService} 를 거쳐 기본 provider 로 처리되는지 검증한다.
 *
 * <p>컨트롤러 → 서비스 → 파일 추출 → JSON 파싱 → 트리 검증까지는 실제 객체를 쓰고,
 * 외부 LLM 호출({@link LlmClient})과 DB 저장({@link GeneratedTreeWriter})만 대체한다.
 */
class ChatControllerDefaultProviderTest {

    private static final String TREE_JSON = """
        {"root":{"name":"루트","memo":null,"children":[]}}
        """;

    private LlmClient openAiClient;
    private LlmClient ollamaClient;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        openAiClient = mock(LlmClient.class);
        ollamaClient = mock(LlmClient.class);
        lenient().when(openAiClient.getProvider()).thenReturn(LlmProvider.OPENAI);
        lenient().when(ollamaClient.getProvider()).thenReturn(LlmProvider.OLLAMA);
        lenient().when(openAiClient.generateTreeJson(anyString())).thenReturn(Mono.just(TREE_JSON));
        lenient().when(ollamaClient.generateTreeJson(anyString())).thenReturn(Mono.just(TREE_JSON));

        GeneratedTreeWriter writer = mock(GeneratedTreeWriter.class);
        when(writer.save(any(), any()))
            .thenReturn(TreeGenResDTO.builder().treeId(42L).nodes(List.of()).build());

        // 빈 주입 순서에 기대지 않도록 OLLAMA 를 먼저 둔다.
        ChatService chatService = new ChatService(
            List.of(ollamaClient, openAiClient),
            JsonMapper.builder().build(),
            new TreeValidator(),
            writer,
            new DocumentTextExtractor(List.of(new PlainTextParser())));

        mockMvc = MockMvcBuilders
            .standaloneSetup(new ChatController(chatService, mock(MockResponseLoader.class)))
            .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
            .setControllerAdvice(new GeneralExceptionAdvice())
            .build();
    }

    @Test
    @DisplayName("multipart 요청에서 provider 를 생략하면 기본 provider(OPENAI)로 트리를 생성해 201")
    void multipartWithoutProviderUsesDefault() throws Exception {
        MockMultipartFile file =
            new MockMultipartFile("file", "note.md", "text/markdown", "본문".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/tree-request")
                .file(file)
                .param("message", "3단계로 정리해줘"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.treeId").value(42));

        verify(openAiClient).generateTreeJson(anyString());
        verify(ollamaClient, never()).generateTreeJson(anyString());
    }

    @Test
    @DisplayName("JSON 요청에서 provider 를 생략해도 기본 provider(OPENAI)로 트리를 생성해 201")
    void jsonWithoutProviderUsesDefault() throws Exception {
        mockMvc.perform(post("/tree-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"트리 만들어줘\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.treeId").value(42));

        verify(openAiClient).generateTreeJson(anyString());
        verify(ollamaClient, never()).generateTreeJson(anyString());
    }

    @Test
    @DisplayName("provider 를 명시하면 기본값 대신 해당 provider 를 쓴다")
    void explicitProviderIsRespected() throws Exception {
        mockMvc.perform(multipart("/tree-request")
                .param("message", "트리 만들어줘")
                .param("provider", "OLLAMA"))
            .andExpect(status().isCreated());

        verify(ollamaClient).generateTreeJson(anyString());
        verify(openAiClient, never()).generateTreeJson(anyString());
    }
}

package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.prompt.TreePrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 트리 생성 오케스트레이션.
 *
 * <p>흐름: provider 선택 → LLM 호출(트랜잭션 밖, 타임아웃 적용) → JSON 파싱 → 구조 검증 → DB 저장.
 * LLM 응답을 기다리는 동안 DB 커넥션을 점유하지 않도록, 저장만 {@link GeneratedTreeWriter} 의
 * 트랜잭션 안에서 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** LLM 응답 대기 상한. WebClient responseTimeout(60s)보다 약간 크게 잡아 안전망으로 둔다. */
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(65);

    private final List<LlmClient> llmClients;
    private final ObjectMapper objectMapper;
    private final TreeValidator treeValidator;
    private final GeneratedTreeWriter generatedTreeWriter;
    private final DocumentTextExtractor documentTextExtractor;

    public TreeGenResDTO generateTree(Long memberId, ChatReqDTO req) {
        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new ChatException(ChatErrorCode.EMPTY_MESSAGE);
        }

        return generate(memberId, req.provider(), TreePrompt.userMessage(req.message(), null));
    }

    /**
     * 파일 입력을 함께 받는 트리 생성.
     *
     * <p>지시문과 파일 중 최소 하나는 있어야 한다. 파일은 본문만 추출해 쓰고 원본은 저장하지 않는다.
     *
     * @param provider LLM 제공자
     * @param message  사용자 지시문 (선택)
     * @param file     업로드 파일 (선택)
     */
    public TreeGenResDTO generateTree(Long memberId, LlmProvider provider, String message, MultipartFile file) {
        boolean hasMessage = message != null && !message.isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasMessage && !hasFile) {
            throw new ChatException(ChatErrorCode.INPUT_REQUIRED);
        }

        // multipart 요청은 @RequestParam 으로 받아 @Valid 를 걸 DTO 가 없으므로, JSON 경로의
        // ChatReqDTO(@Size)와 같은 상한을 여기서 직접 적용한다.
        if (hasMessage && message.length() > ChatReqDTO.MESSAGE_MAX_LENGTH) {
            throw new ChatException(ChatErrorCode.MESSAGE_TOO_LONG);
        }

        // 파일 검증을 LLM 호출 전에 끝내, 잘못된 입력으로 비용이 발생하지 않게 한다.
        String documentText = hasFile ? documentTextExtractor.extract(file) : null;

        return generate(memberId, provider, TreePrompt.userMessage(message, documentText));
    }

    private TreeGenResDTO generate(Long memberId, LlmProvider provider, String userMessage) {
        LlmClient client = resolveClient(provider);

        String json = callLlm(client, userMessage);
        LlmTreeDTO parsed = parse(json);
        treeValidator.validate(parsed);

        return generatedTreeWriter.save(memberId, parsed);
    }

    private LlmClient resolveClient(LlmProvider provider) {
        return llmClients.stream()
            .filter(c -> c.getProvider() == provider)
            .findFirst()
            .orElseThrow(() -> new ChatException(ChatErrorCode.UNSUPPORTED_PROVIDER));
    }

    private String callLlm(LlmClient client, String message) {
        try {
            return client.generateTreeJson(message)
                .timeout(LLM_TIMEOUT)
                .block();
        } catch (ChatException e) {
            throw e;
        } catch (RuntimeException e) {
            if (hasCause(e, TimeoutException.class)) {
                log.warn("LLM 응답 타임아웃");
                throw new ChatException(ChatErrorCode.LLM_TIMEOUT);
            }
            log.error("LLM 호출 실패", e);
            throw new ChatException(ChatErrorCode.LLM_CALL_FAILED);
        }
    }

    private LlmTreeDTO parse(String json) {
        if (json == null || json.isBlank()) {
            throw new ChatException(ChatErrorCode.LLM_RESPONSE_INVALID);
        }
        try {
            return objectMapper.readValue(json, LlmTreeDTO.class);
        } catch (JacksonException e) {
            log.warn("LLM 응답 JSON 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.LLM_RESPONSE_INVALID);
        }
    }

    private boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}

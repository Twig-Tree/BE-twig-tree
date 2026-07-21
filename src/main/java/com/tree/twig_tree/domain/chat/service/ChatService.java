package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.dto.ChatReqDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.TreeGenResDTO;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    public TreeGenResDTO generateTree(ChatReqDTO req) {
        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new ChatException(ChatErrorCode.EMPTY_MESSAGE);
        }

        LlmClient client = resolveClient(req);

        String json = callLlm(client, req.message());
        LlmTreeDTO parsed = parse(json);
        treeValidator.validate(parsed);

        return generatedTreeWriter.save(parsed);
    }

    private LlmClient resolveClient(ChatReqDTO req) {
        return llmClients.stream()
            .filter(c -> c.getProvider() == req.provider())
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

package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.client.StructuredJsonResult;
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
 * <p>흐름: provider 선택 → LLM 호출(트랜잭션 밖, 타임아웃 적용) → JSON 파싱 → 구조 검증(실패 시 1회 재생성) → DB 저장.
 * LLM 응답을 기다리는 동안 DB 커넥션을 점유하지 않도록, 저장만 {@link GeneratedTreeWriter} 의
 * 트랜잭션 안에서 수행한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    /** LLM 응답 대기 상한. WebClient responseTimeout(60s)보다 약간 크게 잡아 안전망으로 둔다. */
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(65);

    /** 재생성까지 포함한 LLM 응답 대기 총량. 요청 스레드가 시도마다 상한만큼 묶이지 않게 한다. */
    private static final Duration GENERATION_BUDGET = Duration.ofSeconds(90);

    /** 유효하지 않은 응답을 받았을 때를 포함한 트리 생성 시도 횟수 상한. 평가 하네스도 같은 값을 쓴다. */
    public static final int MAX_GENERATION_ATTEMPTS = 2;

    /** 요청에서 provider 를 생략했을 때 사용할 제공자. */
    static final LlmProvider DEFAULT_PROVIDER = LlmProvider.OPENAI;

    private final List<LlmClient> llmClients;
    private final ObjectMapper objectMapper;
    private final TreeValidator treeValidator;
    private final GeneratedTreeWriter generatedTreeWriter;
    private final DocumentTextExtractor documentTextExtractor;

    public TreeGenResDTO generateTree(Long memberId, ChatReqDTO req) {
        if (req == null || req.message() == null || req.message().isBlank()) {
            throw new ChatException(ChatErrorCode.EMPTY_MESSAGE);
        }
        validateMessageLength(req.message());

        return generate(memberId, req.provider(), TreePrompt.userMessage(req.message(), null));
    }

    /**
     * 파일 입력을 함께 받는 트리 생성.
     *
     * <p>지시문과 파일 중 최소 하나는 있어야 한다. 파일은 본문만 추출해 쓰고 원본은 저장하지 않는다.
     *
     * @param provider LLM 제공자 (선택, 생략 시 {@link #DEFAULT_PROVIDER})
     * @param message  사용자 지시문 (선택)
     * @param file     업로드 파일 (선택)
     */
    public TreeGenResDTO generateTree(Long memberId, LlmProvider provider, String message, MultipartFile file) {
        boolean hasMessage = message != null && !message.isBlank();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasMessage && !hasFile) {
            throw new ChatException(ChatErrorCode.INPUT_REQUIRED);
        }
        if (hasMessage) {
            validateMessageLength(message);
        }

        // 파일 검증을 LLM 호출 전에 끝내, 잘못된 입력으로 비용이 발생하지 않게 한다.
        String documentText = hasFile ? documentTextExtractor.extract(file) : null;

        return generate(memberId, provider, TreePrompt.userMessage(message, documentText));
    }

    /**
     * 지시문 길이 상한 검사.
     *
     * <p>컨트롤러의 {@code @Valid} 가 먼저 막지만, 이 서비스를 다른 경로에서 직접 호출해도
     * 큰 프롬프트가 LLM 에 실리지 않도록 서비스에서도 한 번 더 확인한다.
     */
    private void validateMessageLength(String message) {
        if (message.length() > ChatReqDTO.MESSAGE_MAX_LENGTH) {
            throw new ChatException(ChatErrorCode.MESSAGE_TOO_LONG);
        }
    }

    private TreeGenResDTO generate(Long memberId, LlmProvider provider, String userMessage) {
        LlmClient client = resolveClient(provider);

        return generatedTreeWriter.save(memberId, generateValidTree(client, userMessage));
    }

    /**
     * 검증을 통과한 트리를 생성한다.
     *
     * <p>노드 수 초과 같은 응답 오류는 호출마다 달라지므로 같은 요청을 다시 보낸다. 호출 실패·타임아웃은
     * 대기 시간이 두 배가 되므로, 모델의 거절은 다시 보내도 결과가 같으므로 재시도하지 않는다.
     */
    private LlmTreeDTO generateValidTree(LlmClient client, String userMessage) {
        long generationStartedAt = System.nanoTime();

        for (int attempt = 1; ; attempt++) {
            long startedAt = System.nanoTime();
            Duration timeout = attemptTimeout(Duration.ofNanos(startedAt - generationStartedAt));
            StructuredJsonResult result = callLlm(client, userMessage, timeout);
            logResponse(client.getProvider(), result, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());

            try {
                LlmTreeDTO parsed = parse(result == null ? null : result.content());
                // 검증에서 거부되는 트리의 모양도 볼 수 있도록 검증 전에 기록한다.
                logTreeMetrics(TreeMetrics.from(parsed));
                treeValidator.validate(parsed);
                return parsed;
            } catch (ChatException e) {
                if (attempt >= MAX_GENERATION_ATTEMPTS || result == null || result.isRefused()) {
                    throw e;
                }
                log.warn("LLM 트리 응답이 유효하지 않아 다시 생성합니다 (attempt={}/{})", attempt, MAX_GENERATION_ATTEMPTS);
            }
        }
    }

    private LlmClient resolveClient(LlmProvider provider) {
        LlmProvider target = provider == null ? DEFAULT_PROVIDER : provider;
        return llmClients.stream()
            .filter(c -> c.getProvider() == target)
            .findFirst()
            .orElseThrow(() -> new ChatException(ChatErrorCode.UNSUPPORTED_PROVIDER));
    }

    /** 이번 시도의 응답 대기 상한. 시도별 상한과 전체 예산의 남은 시간 중 짧은 쪽이다. 평가 하네스도 같은 값을 쓴다. */
    public static Duration attemptTimeout(Duration elapsed) {
        Duration remaining = GENERATION_BUDGET.minus(elapsed);
        return remaining.compareTo(LLM_TIMEOUT) < 0 ? remaining : LLM_TIMEOUT;
    }

    /** 품질 비교와 실패 원인 분류에 쓰는 응답 메타데이터를 남긴다. 사용자 입력과 응답 본문은 기록하지 않는다. */
    private void logResponse(LlmProvider provider, StructuredJsonResult result, long latencyMs) {
        if (result == null) {
            log.warn("LLM 응답 없음 (provider={}, latencyMs={})", provider, latencyMs);
            return;
        }
        log.info("LLM 트리 응답 (provider={}, model={}, finishReason={}, inputTokens={}, outputTokens={}, latencyMs={})",
            provider, result.model(), result.finishReason(), result.inputTokens(), result.outputTokens(), latencyMs);

        if (result.isTruncated()) {
            log.warn("LLM 트리 응답이 토큰 상한에 걸려 잘렸습니다 (provider={}, model={}, outputTokens={})",
                provider, result.model(), result.outputTokens());
        }
        if (result.isRefused()) {
            log.warn("LLM 이 트리 생성을 거절했습니다 (provider={}, model={}): {}",
                provider, result.model(), result.refusal());
        }
    }

    private void logTreeMetrics(TreeMetrics metrics) {
        log.info("LLM 트리 지표 (nodes={}, maxDepth={}, leaves={}, singleChild={}, shellCandidates={}, "
                + "duplicateSiblings={}, memos={})",
            metrics.nodeCount(), metrics.maxDepth(), metrics.leafCount(), metrics.singleChildCount(),
            metrics.shellCandidateCount(), metrics.duplicateSiblingCount(), metrics.memoCount());
    }

    private StructuredJsonResult callLlm(LlmClient client, String message, Duration timeout) {
        try {
            return client.generateTreeJson(message)
                .timeout(timeout)
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

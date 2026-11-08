package com.tree.twig_tree.domain.lecture.service;

import com.tree.twig_tree.domain.chat.client.LlmClient;
import com.tree.twig_tree.domain.chat.client.LlmProvider;
import com.tree.twig_tree.domain.chat.client.StructuredJsonRequest;
import com.tree.twig_tree.domain.chat.client.StructuredJsonResult;
import com.tree.twig_tree.domain.lecture.dto.LectureGenResDTO;
import com.tree.twig_tree.domain.lecture.dto.LectureReqDTO;
import com.tree.twig_tree.domain.lecture.exception.LectureException;
import com.tree.twig_tree.domain.lecture.exception.code.LectureErrorCode;
import com.tree.twig_tree.domain.lecture.prompt.LecturePrompt;
import com.tree.twig_tree.domain.tree.exception.TreeException;
import com.tree.twig_tree.domain.tree.exception.code.TreeErrorCode;
import com.tree.twig_tree.domain.tree.repository.TreeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 강의 시나리오 생성 오케스트레이션.
 *
 * <p>흐름: 소유권 확인 → 스키마 버전 확인 → repair 정책 확인 → 프롬프트 조립 →
 * strict JSON Schema 호출(실패 시 1회 재시도) → 응답 본문을 그대로 반환.
 *
 * <p>시나리오를 파싱하지도, 검증하지도, 저장하지도 않는다. 검사와 컴파일은 프론트엔드가 하고,
 * 실패하면 {@code repair} 를 담아 다시 요청한다.
 */
@Slf4j
@Service
public class LectureGenerationService {

    /** LLM 응답 대기 상한. 트리 생성({@code ChatService})과 같은 값으로 둔다. */
    private static final Duration LLM_TIMEOUT = Duration.ofSeconds(65);

    /** 모델 호출 횟수 상한. 최초 1회 + 실패 재시도 1회. */
    private static final int MAX_ATTEMPTS = 2;

    /** 요청에서 provider 를 생략했을 때 사용할 제공자. */
    static final LlmProvider DEFAULT_PROVIDER = LlmProvider.OPENAI;

    private final List<LlmClient> llmClients;
    private final TreeRepository treeRepository;
    private final LectureScenarioResources resources;
    private final String model;
    private final int maxOutputTokens;

    public LectureGenerationService(
        List<LlmClient> llmClients,
        TreeRepository treeRepository,
        LectureScenarioResources resources,
        @Value("${openai.lecture-model}") String model,
        @Value("${openai.lecture-max-tokens}") int maxOutputTokens
    ) {
        this.llmClients = llmClients;
        this.treeRepository = treeRepository;
        this.resources = resources;
        this.model = model;
        this.maxOutputTokens = maxOutputTokens;
    }

    public LectureGenResDTO generate(Long memberId, LectureReqDTO req) {
        // 잘못된 입력으로 모델 비용이 발생하지 않도록 호출 전에 모두 확인한다.
        validateTreeOwner(memberId, req.treeId());
        validateSchemaVersion(req.scenarioSchemaVersion());
        validateRepair(req.repair());
        LlmClient client = resolveClient(req.provider());

        return call(client, req, buildRequest(req));
    }

    /**
     * 모델 호출. 호출 실패와 타임아웃만 재시도하고, 응답이 왔다면 그 응답으로 끝낸다.
     *
     * <p>잘린 응답({@code finish_reason: "length"})과 거절은 다시 불러도 같을 가능성이 높아 바로 오류다.
     * 시나리오 내용이 틀린 경우는 백엔드가 판단하지 않고 그대로 넘긴다. 프론트엔드가 검사한 뒤
     * {@code repair} 로 다시 요청한다.
     */
    private LectureGenResDTO call(LlmClient client, LectureReqDTO req, StructuredJsonRequest llmRequest) {
        boolean repair = req.repair() != null;
        long inputTokens = 0;
        long outputTokens = 0;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            StructuredJsonResult result;
            try {
                result = client.generateStructuredJson(llmRequest)
                    .timeout(LLM_TIMEOUT)
                    .block();
            } catch (RuntimeException e) {
                lastFailure = e;
                log.warn("강의 시나리오 생성 호출 실패 (treeId={}, attempt={}/{}, repair={}): {}",
                    req.treeId(), attempt, MAX_ATTEMPTS, repair, e.toString());
                continue;
            }

            if (result == null) {
                lastFailure = new IllegalStateException("LLM 응답이 비어 있습니다.");
                log.warn("강의 시나리오 생성 응답 없음 (treeId={}, attempt={}/{}, repair={})",
                    req.treeId(), attempt, MAX_ATTEMPTS, repair);
                continue;
            }

            inputTokens += result.inputTokens();
            outputTokens += result.outputTokens();

            log.info("강의 시나리오 생성 (treeId={}, model={}, promptVersion={}, inputTokens={}, "
                    + "outputTokens={}, finishReason={}, attempt={}/{}, repair={})",
                req.treeId(), result.model(), LecturePrompt.PROMPT_VERSION, result.inputTokens(),
                result.outputTokens(), result.finishReason(), attempt, MAX_ATTEMPTS, repair);

            if (result.isRefused()) {
                log.warn("강의 시나리오 생성 거절 (treeId={}): {}", req.treeId(), result.refusal());
                throw new LectureException(LectureErrorCode.MODEL_REFUSED);
            }
            if (result.isTruncated()) {
                throw new LectureException(LectureErrorCode.RESPONSE_TRUNCATED);
            }
            if (result.hasEmptyContent()) {
                throw new LectureException(LectureErrorCode.RESPONSE_EMPTY);
            }

            return LectureGenResDTO.builder()
                .responseText(result.content())
                .model(result.model())
                .promptVersion(LecturePrompt.PROMPT_VERSION)
                .usage(new LectureGenResDTO.Usage(inputTokens, outputTokens))
                .attemptCount(attempt)
                .build();
        }

        throw toCallException(lastFailure);
    }

    private StructuredJsonRequest buildRequest(LectureReqDTO req) {
        List<StructuredJsonRequest.Message> messages = new ArrayList<>();
        messages.add(StructuredJsonRequest.Message.system(
            LecturePrompt.system(resources.vocabularyJson())));
        messages.add(StructuredJsonRequest.Message.user(
            LecturePrompt.userMessage(req.treeOutline(), req.request())));

        // repair 는 직전 응답을 assistant 로 싣고 고칠 문제를 user 로 덧붙인다.
        // outline 과 어휘는 첫 요청과 같은 내용을 그대로 다시 싣는다.
        if (req.repair() != null) {
            messages.add(StructuredJsonRequest.Message.assistant(req.repair().previousResponseText()));
            messages.add(StructuredJsonRequest.Message.user(LecturePrompt.repairMessage(req.repair())));
        }

        return new StructuredJsonRequest(
            model, maxOutputTokens, LecturePrompt.SCHEMA_NAME, resources.strictSchema(), messages);
    }

    /**
     * 트리 소유자 확인. 판정은 {@code NodeService.validateTreeOwner} 와 같다.
     *
     * <p>소유자 ID 만 조회해 트랜잭션 없이 확인한다. LLM 응답을 기다리는 동안 DB 커넥션을 쥐고 있지
     * 않도록, 이 메서드 밖에서는 DB 를 건드리지 않는다.
     */
    private void validateTreeOwner(Long memberId, Long treeId) {
        Long ownerId = treeRepository.findOwnerMemberIdById(treeId)
            .orElseThrow(() -> new TreeException(TreeErrorCode.TREE_NOT_FOUND));

        if (!ownerId.equals(memberId)) {
            throw new TreeException(TreeErrorCode.TREE_ACCESS_DENIED);
        }
    }

    /**
     * 요청한 스키마 버전이 서버가 가진 버전과 같은지 확인한다.
     *
     * <p>한쪽만 배포됐을 때 어긋난 스키마로 모델을 부르지 않기 위해서다.
     */
    private void validateSchemaVersion(Integer requested) {
        if (requested == null || requested != resources.scenarioSchemaVersion()) {
            log.warn("시나리오 스키마 버전 불일치: 요청 {}, 서버 {}", requested, resources.scenarioSchemaVersion());
            throw new LectureException(LectureErrorCode.SCHEMA_VERSION_MISMATCH);
        }
    }

    /** repair 는 프론트엔드 정책상 최대 1회다. 비용 상한을 서버에서도 지킨다. */
    private void validateRepair(LectureReqDTO.Repair repair) {
        if (repair == null) {
            return;
        }
        if (repair.attempt() == null || repair.attempt() < 1
            || repair.attempt() > LectureReqDTO.MAX_REPAIR_ATTEMPT) {
            throw new LectureException(LectureErrorCode.REPAIR_NOT_ALLOWED);
        }
    }

    /** strict JSON Schema 를 지원하는 제공자만 쓸 수 있다. */
    private LlmClient resolveClient(LlmProvider provider) {
        LlmProvider target = provider == null ? DEFAULT_PROVIDER : provider;

        return llmClients.stream()
            .filter(c -> c.getProvider() == target)
            .filter(LlmClient::supportsStrictJsonSchema)
            .findFirst()
            .orElseThrow(() -> new LectureException(LectureErrorCode.UNSUPPORTED_PROVIDER));
    }

    private LectureException toCallException(RuntimeException lastFailure) {
        if (lastFailure instanceof LectureException e) {
            return e;
        }
        if (hasCause(lastFailure, TimeoutException.class)) {
            log.warn("강의 시나리오 생성 타임아웃");
            return new LectureException(LectureErrorCode.LLM_TIMEOUT);
        }
        log.error("강의 시나리오 생성 호출 실패", lastFailure);
        return new LectureException(LectureErrorCode.LLM_CALL_FAILED);
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

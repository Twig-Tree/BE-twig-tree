package com.tree.twig_tree.domain.chat.eval;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.tree.twig_tree.domain.chat.client.OpenAiLlmClient;
import com.tree.twig_tree.domain.chat.service.TreeValidator;
import com.tree.twig_tree.global.config.OpenAiConfig;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 평가 세트를 실제 OpenAI API 로 실행하는 진입점. 비용이 발생하므로 {@code test} 태스크에서는 돌지 않는다.
 *
 * <p>실행: {@code ./gradlew treeEval --console=plain --quiet}. 결과는 {@code build/tree-eval/<모델>-<시각>/} 의
 * {@code summary.md} 와 {@code runs.jsonl} 에 저장된다.
 *
 * <p>환경변수 ({@code .env} 도 읽으며 셸 환경변수가 우선한다):
 * <ul>
 *   <li>{@code OPENAI_API_KEY}: 필수</li>
 *   <li>{@code OPENAI_MODEL}: 평가할 모델. 기본 gpt-6-luna</li>
 *   <li>{@code OPENAI_TREE_MAX_TOKENS}: 응답 토큰 상한. 기본 16000</li>
 *   <li>{@code TREE_EVAL_REPEATS}: 케이스별 반복 횟수. 기본 3</li>
 *   <li>{@code TREE_EVAL_CONCURRENCY}: 동시 호출 수. 기본 4</li>
 *   <li>{@code TREE_EVAL_CATEGORIES}: 실행할 카테고리(쉼표 구분). topic, depth, instruction, document. 기본 전체</li>
 *   <li>{@code TREE_EVAL_JUDGE_MODEL}: 의미 품질 채점 모델. 기본 채점 안 함</li>
 * </ul>
 */
public final class TreeEvalMain {

    private TreeEvalMain() {
    }

    public static void main(String[] args) throws IOException, InterruptedException, ExecutionException {
        // 설정 파일 없는 Logback 기본값은 DEBUG 라 요청 처리 로그가 출력을 덮는다.
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.ERROR);

        String apiKey = env("OPENAI_API_KEY", "");
        if (apiKey.isBlank() || "dummy".equals(apiKey)) {
            System.err.println("OPENAI_API_KEY 가 없습니다. .env 또는 환경변수로 지정하세요.");
            System.exit(2);
        }

        String model = env("OPENAI_MODEL", "gpt-6-luna");
        String judgeModel = env("TREE_EVAL_JUDGE_MODEL", "");
        int repeats = Integer.parseInt(env("TREE_EVAL_REPEATS", "3"));
        int concurrency = Integer.parseInt(env("TREE_EVAL_CONCURRENCY", "4"));
        int maxTokens = Integer.parseInt(env("OPENAI_TREE_MAX_TOKENS", "16000"));
        String baseUrl = env("OPENAI_BASE_URL", "https://api.openai.com/v1");

        ObjectMapper mapper = JsonMapper.builder().build();
        WebClient webClient = new OpenAiConfig().openAiWebClient(apiKey, baseUrl);
        OpenAiLlmClient client = new OpenAiLlmClient(webClient);
        ReflectionTestUtils.setField(client, "model", model);
        ReflectionTestUtils.setField(client, "treeMaxTokens", maxTokens);

        TreeEvalJudge judge = judgeModel.isBlank() ? null : new TreeEvalJudge(client, mapper, judgeModel);
        TreeEvalRunner runner = new TreeEvalRunner(client, mapper, new TreeValidator(), judge);

        List<TreeEvalCase> cases = selectCases(TreeEvalCases.load(mapper), env("TREE_EVAL_CATEGORIES", ""));
        int total = cases.size() * repeats;
        System.out.printf("[tree-eval] model=%s, judge=%s, cases=%d, repeats=%d, calls=%d%n",
            model, judgeModel.isBlank() ? "off" : judgeModel, cases.size(), repeats, total);

        List<TreeEvalRun> runs = execute(runner, cases, repeats, concurrency, total);
        Path outputDir = write(model, runs, mapper);

        System.out.println(Files.readString(outputDir.resolve("summary.md")));
        System.out.println("[tree-eval] 결과: " + outputDir);

        if (runs.stream().noneMatch(TreeEvalRun::isSuccess)) {
            String firstError = runs.stream().map(TreeEvalRun::error).filter(e -> e != null).findFirst().orElse("없음");
            System.err.println("[tree-eval] 성공한 실행이 없습니다. 첫 오류: " + firstError);
            System.exit(1);
        }
    }

    private static List<TreeEvalCase> selectCases(List<TreeEvalCase> cases, String categories) {
        if (categories.isBlank()) {
            return cases;
        }
        Set<String> selected = Arrays.stream(categories.split(",")).map(String::trim).collect(Collectors.toSet());
        return cases.stream().filter(evalCase -> selected.contains(evalCase.category())).toList();
    }

    private static List<TreeEvalRun> execute(TreeEvalRunner runner, List<TreeEvalCase> cases, int repeats,
                                             int concurrency, int total)
        throws InterruptedException, ExecutionException {
        AtomicInteger done = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(concurrency);
        try {
            List<Future<TreeEvalRun>> futures = new ArrayList<>();
            for (TreeEvalCase evalCase : cases) {
                for (int repeat = 1; repeat <= repeats; repeat++) {
                    int currentRepeat = repeat;
                    futures.add(pool.submit(() -> {
                        TreeEvalRun run = runner.run(evalCase, currentRepeat);
                        System.out.printf("[tree-eval] %d/%d %s#%d %s nodes=%d depth=%d %.1fs%n",
                            done.incrementAndGet(), total, run.caseId(), run.repeat(), run.outcome(),
                            run.metrics().nodeCount(), run.metrics().maxDepth(), run.latencyMs() / 1000.0);
                        return run;
                    }));
                }
            }

            List<TreeEvalRun> runs = new ArrayList<>();
            for (Future<TreeEvalRun> future : futures) {
                runs.add(future.get());
            }
            return runs;
        } finally {
            // 예외로 빠져나올 때 남은 케이스가 API 를 계속 호출하지 않게 대기 작업까지 취소한다.
            pool.shutdownNow();
        }
    }

    private static Path write(String model, List<TreeEvalRun> runs, ObjectMapper mapper) throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path dir = Path.of("build", "tree-eval", model.replaceAll("[^A-Za-z0-9._-]", "_") + "-" + timestamp);
        Files.createDirectories(dir);

        List<String> lines = runs.stream().map(mapper::writeValueAsString).toList();
        Files.write(dir.resolve("runs.jsonl"), lines, StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("summary.md"), TreeEvalReport.render(model, runs), StandardCharsets.UTF_8);
        return dir;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}

package com.tree.twig_tree.domain.chat.eval;

import com.tree.twig_tree.domain.chat.eval.TreeEvalRun.Outcome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/** 실행 결과를 카테고리별 마크다운 표로 집계한다. PR 에 그대로 붙일 수 있는 형태다. */
final class TreeEvalReport {

    private static final String TOTAL = "전체";
    private static final String NOT_APPLICABLE = "-";

    private TreeEvalReport() {
    }

    static String render(String title, List<TreeEvalRun> runs) {
        Map<String, List<TreeEvalRun>> groups = group(runs);
        StringBuilder out = new StringBuilder("# 트리 평가: ").append(title).append("\n\n");

        out.append("## 신뢰성\n\n");
        table(out, groups, List.of("실행", "서버 거부율", "잘림", "거절·빈 응답", "파싱 실패", "검증 거부", "타임아웃", "호출 실패",
            "평균 출력 토큰", "평균 지연(s)", "최대 지연(s)", "재생성 발생"), TreeEvalReport::reliabilityRow);

        out.append("\n## 구조 (지표 평균은 성공한 실행만, 준수율 분모는 판정 대상 실행 전체)\n\n");
        table(out, groups, List.of("깊이 준수", "노드 수 준수", "평균 노드", "평균 최대 깊이", "일자형 비율", "껍데기 후보/트리",
            "형제 중복/트리", "memo 채움"), TreeEvalReport::structureRow);

        if (runs.stream().anyMatch(run -> run.judgement() != null)) {
            out.append("\n## LLM 채점 (참고용 근사치)\n\n");
            table(out, groups, List.of("채점 수", "MECE", "추상화 일관성", "학습 순서", "껍데기 비율", "문서 근거 없는 노드 비율"),
                TreeEvalReport::judgementRow);
        }
        return out.toString();
    }

    private static Map<String, List<TreeEvalRun>> group(List<TreeEvalRun> runs) {
        Map<String, List<TreeEvalRun>> groups = new LinkedHashMap<>();
        for (TreeEvalRun run : runs) {
            groups.computeIfAbsent(run.category(), key -> new ArrayList<>()).add(run);
        }
        groups.put(TOTAL, runs);
        return groups;
    }

    private static void table(StringBuilder out, Map<String, List<TreeEvalRun>> groups, List<String> headers,
                              Function<List<TreeEvalRun>, List<String>> row) {
        out.append("| 구분 | ").append(String.join(" | ", headers)).append(" |\n");
        out.append("| --- |").append(" ---: |".repeat(headers.size())).append('\n');
        groups.forEach((category, runs) ->
            out.append("| ").append(category).append(" | ").append(String.join(" | ", row.apply(runs))).append(" |\n"));
    }

    private static List<String> reliabilityRow(List<TreeEvalRun> runs) {
        return List.of(
            String.valueOf(runs.size()),
            percent(runs.stream().filter(run -> !run.isSuccess()).count(), runs.size()),
            count(runs, Outcome.TRUNCATED),
            count(runs, Outcome.REFUSED, Outcome.EMPTY),
            count(runs, Outcome.PARSE_FAILED),
            count(runs, Outcome.VALIDATION_FAILED),
            count(runs, Outcome.TIMEOUT),
            count(runs, Outcome.CALL_FAILED),
            decimal(runs, TreeEvalRun::isSuccess, run -> run.outputTokens(), 0),
            decimal(runs, run -> true, run -> run.latencyMs() / 1000.0, 1),
            String.format(Locale.ROOT, "%.1f", runs.stream().mapToLong(TreeEvalRun::latencyMs).max().orElse(0) / 1000.0),
            String.valueOf(runs.stream().filter(run -> run.attempts() > 1).count())
        );
    }

    private static List<String> structureRow(List<TreeEvalRun> runs) {
        Predicate<TreeEvalRun> success = TreeEvalRun::isSuccess;
        return List.of(
            complianceRate(runs, TreeEvalRun::depthMatched),
            complianceRate(runs, TreeEvalRun::nodesInRange),
            decimal(runs, success, run -> run.metrics().nodeCount(), 1),
            decimal(runs, success, run -> run.metrics().maxDepth(), 1),
            percentAverage(runs, success, run -> run.metrics().singleChildRatio()),
            decimal(runs, success, run -> run.metrics().shellCandidateCount(), 2),
            decimal(runs, success, run -> run.metrics().duplicateSiblingCount(), 2),
            percentAverage(runs, success, run -> run.metrics().memoFillRate())
        );
    }

    private static List<String> judgementRow(List<TreeEvalRun> runs) {
        Predicate<TreeEvalRun> judged = run -> run.judgement() != null;
        Predicate<TreeEvalRun> documentJudged = judged.and(run -> run.judgement().ungroundedNodeCount() != null);
        return List.of(
            String.valueOf(runs.stream().filter(judged).count()),
            decimal(runs, judged, run -> run.judgement().mece(), 2),
            decimal(runs, judged, run -> run.judgement().granularity(), 2),
            decimal(runs, judged, run -> run.judgement().ordering(), 2),
            percentAverage(runs, judged, run -> nodeRatio(run, run.judgement().shellNodeCount())),
            percentAverage(runs, documentJudged, run -> nodeRatio(run, run.judgement().ungroundedNodeCount()))
        );
    }

    private static double nodeRatio(TreeEvalRun run, int count) {
        return run.metrics().nodeCount() == 0 ? 0 : (double) count / run.metrics().nodeCount();
    }

    private static String complianceRate(List<TreeEvalRun> runs, Function<TreeEvalRun, Boolean> verdict) {
        List<Boolean> verdicts = runs.stream().map(verdict).filter(value -> value != null).toList();
        if (verdicts.isEmpty()) {
            return NOT_APPLICABLE;
        }
        return percent(verdicts.stream().filter(Boolean::booleanValue).count(), verdicts.size());
    }

    private static String count(List<TreeEvalRun> runs, Outcome... outcomes) {
        List<Outcome> targets = List.of(outcomes);
        return String.valueOf(runs.stream().filter(run -> targets.contains(run.outcome())).count());
    }

    private static String decimal(List<TreeEvalRun> runs, Predicate<TreeEvalRun> filter,
                                  ToDoubleFunction<TreeEvalRun> value, int digits) {
        var average = runs.stream().filter(filter).mapToDouble(value).average();
        return average.isPresent() ? String.format(Locale.ROOT, "%." + digits + "f", average.getAsDouble()) : NOT_APPLICABLE;
    }

    private static String percentAverage(List<TreeEvalRun> runs, Predicate<TreeEvalRun> filter,
                                         ToDoubleFunction<TreeEvalRun> value) {
        var average = runs.stream().filter(filter).mapToDouble(value).average();
        return average.isPresent() ? String.format(Locale.ROOT, "%.1f%%", average.getAsDouble() * 100) : NOT_APPLICABLE;
    }

    private static String percent(long part, long whole) {
        return whole == 0 ? NOT_APPLICABLE : String.format(Locale.ROOT, "%.1f%%", part * 100.0 / whole);
    }
}

package com.tree.twig_tree.domain.lecture.prompt;

import com.tree.twig_tree.domain.lecture.dto.LectureReqDTO;

import java.util.stream.Collectors;

/**
 * 강의 시나리오 생성용 LLM 프롬프트.
 *
 * <p>모델이 고를 수 있는 어휘(intent, role, delivery)와 규칙은 프론트엔드가 만든 어휘 파일에 있고,
 * 이 클래스는 그 내용을 요약하지 않고 그대로 system 프롬프트에 싣는다. 백엔드가 다시 쓰면 프론트엔드의
 * 컴파일러·검사와 어긋나기 때문이다.
 *
 * <p>응답 모양은 strict JSON Schema 가 강제하므로 프롬프트에 스키마를 글로 다시 적지 않는다.
 */
public final class LecturePrompt {

    private LecturePrompt() {
    }

    /**
     * 프롬프트 버전.
     *
     * <p>프론트엔드 평가 보고서가 이 값을 모델 이름과 함께 묶어 쓴다. system 프롬프트나 메시지 구성을
     * 바꿀 때마다 올린다. 보고서 표에 그대로 들어가므로 영문자, 숫자, {@code . _ : -} 만 쓴다.
     */
    public static final String PROMPT_VERSION = "lecture-v1";

    /** strict 모드에 넘기는 스키마 이름. */
    public static final String SCHEMA_NAME = "lecture_scenario";

    /**
     * system 메시지.
     *
     * @param vocabularyJson 어휘 파일({@code lecture-generation-vocabulary.json}) 전문
     */
    public static String system(String vocabularyJson) {
        return """
            너는 지식 트리를 바탕으로 강의 시나리오를 짜는 도우미다.
            사용자의 요청을 읽고, 트리의 어떤 노드를 어떤 순서로 가르칠지 장면(scene)과 비트(beat)로 나눈다.

            너는 "무엇을 가르칠지"만 정한다. 화면 효과의 종류, 시간, 색, easing 같은 값은 정하지 않는다.
            그 값은 서비스가 intent 와 delivery 를 보고 정한다.

            내레이션은 사용자의 요청과 같은 언어로 쓴다.

            아래는 네가 쓸 수 있는 어휘와 규칙이다. 여기에 없는 intent, role, delivery 값은 쓰지 않는다.

            %s
            """.formatted(vocabularyJson);
    }

    /**
     * 첫 요청의 user 메시지.
     *
     * @param treeOutline 프론트엔드가 만든 트리 outline
     * @param request     사용자 요청 문장
     */
    public static String userMessage(String treeOutline, String request) {
        return """
            [트리]
            %s

            [요청]
            %s
            """.formatted(treeOutline, request);
    }

    /**
     * repair 요청의 user 메시지.
     *
     * <p>직전 응답은 assistant 메시지로 따로 싣고, 이 메시지는 고쳐야 할 문제만 알려 준다.
     */
    public static String repairMessage(LectureReqDTO.Repair repair) {
        String issues = repair.issues().stream()
            .map(LecturePrompt::issueLine)
            .collect(Collectors.joining("\n"));

        String omitted = repair.omittedIssueCount() == null || repair.omittedIssueCount() <= 0
            ? ""
            : "\n이 밖에 알려 주지 못한 문제가 %d개 더 있다.\n".formatted(repair.omittedIssueCount());

        return """
            직전 응답에서 아래 문제가 발견됐다.

            %s
            %s
            같은 형식으로 고친 전체 시나리오를 다시 써라. 문제가 없던 부분은 그대로 두고, 설명 문장은 붙이지 않는다.
            """.formatted(issues, omitted);
    }

    private static String issueLine(LectureReqDTO.Repair.Issue issue) {
        String path = issue.path() == null || issue.path().isBlank() ? "(위치 없음)" : issue.path();
        return "- %s: %s".formatted(path, issue.message());
    }
}

package com.tree.twig_tree.domain.lecture.prompt;

import com.tree.twig_tree.domain.lecture.dto.LectureReqDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LecturePromptTest {

    private static final String VOCABULARY = """
        { "scenarioSchemaVersion": 1, "intents": [{ "intent": "compare" }],
          "rules": ["노드는 트리 outline의 대괄호 안 ID 문자열로 가리킨다."] }
        """;

    @Test
    @DisplayName("프롬프트 버전은 프론트엔드 평가가 허용하는 문자만 쓴다")
    void promptVersionFormat() {
        // 프론트엔드 평가 보고서가 이 값을 표에 그대로 쓰므로 공백과 마크다운 문자를 허용하지 않는다.
        assertThat(LecturePrompt.PROMPT_VERSION).matches("[A-Za-z0-9._:-]+");
    }

    @Test
    @DisplayName("system 메시지는 어휘 파일을 요약하지 않고 그대로 싣는다")
    void systemCarriesVocabulary() {
        String system = LecturePrompt.system(VOCABULARY);

        assertThat(system).contains(VOCABULARY);
        assertThat(system).contains("무엇을 가르칠지");
    }

    @Test
    @DisplayName("user 메시지에 트리 outline 과 요청 문장이 들어간다")
    void userMessageCarriesOutlineAndRequest() {
        String user = LecturePrompt.userMessage("- [1] 척추동물\n  - [2] 포유류", "비교해서 설명해 줘.");

        assertThat(user).contains("- [1] 척추동물");
        assertThat(user).contains("  - [2] 포유류");
        assertThat(user).contains("비교해서 설명해 줘.");
    }

    @Test
    @DisplayName("repair 메시지에 문제의 경로와 설명이 모두 들어간다")
    void repairMessageListsIssues() {
        LectureReqDTO.Repair repair = new LectureReqDTO.Repair(1, "{}", List.of(
            new LectureReqDTO.Repair.Issue("node", "$.scenes[0].beats[0].cues[0].roles.subject",
                "트리에 없는 노드(999)입니다."),
            new LectureReqDTO.Repair.Issue("schema", "$.scenes[1].id", "장면 ID가 겹칩니다.")
        ), 0);

        String message = LecturePrompt.repairMessage(repair);

        assertThat(message).contains("$.scenes[0].beats[0].cues[0].roles.subject");
        assertThat(message).contains("트리에 없는 노드(999)입니다.");
        assertThat(message).contains("$.scenes[1].id");
        assertThat(message).contains("장면 ID가 겹칩니다.");
        assertThat(message).contains("전체 시나리오를 다시 써라");
    }

    @Test
    @DisplayName("보내지 못한 문제가 있으면 그 수를 알려 주고, 없으면 적지 않는다")
    void repairMessageMentionsOmittedCount() {
        LectureReqDTO.Repair.Issue issue =
            new LectureReqDTO.Repair.Issue("compile", "$.scenes[0]", "cue 가 없습니다.");

        assertThat(LecturePrompt.repairMessage(new LectureReqDTO.Repair(1, "{}", List.of(issue), 3)))
            .contains("3개");
        assertThat(LecturePrompt.repairMessage(new LectureReqDTO.Repair(1, "{}", List.of(issue), 0)))
            .doesNotContain("더 있다");
        assertThat(LecturePrompt.repairMessage(new LectureReqDTO.Repair(1, "{}", List.of(issue), null)))
            .doesNotContain("더 있다");
    }

    @Test
    @DisplayName("경로가 없는 문제도 설명은 전달한다")
    void repairMessageWithoutPath() {
        LectureReqDTO.Repair repair = new LectureReqDTO.Repair(1, "{}", List.of(
            new LectureReqDTO.Repair.Issue("json", "", "JSON 으로 읽을 수 없습니다.")
        ), 0);

        String message = LecturePrompt.repairMessage(repair);

        assertThat(message).contains("(위치 없음)");
        assertThat(message).contains("JSON 으로 읽을 수 없습니다.");
    }
}

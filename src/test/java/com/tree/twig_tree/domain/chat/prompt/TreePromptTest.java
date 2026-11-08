package com.tree.twig_tree.domain.chat.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TreePromptTest {

    @Test
    @DisplayName("노드 스키마의 키 순서는 name, memo, children 으로 고정된다 — 모델이 이 순서대로 생성한다")
    @SuppressWarnings("unchecked")
    void nodePropertiesKeepGenerationOrder() {
        Map<String, Object> defs = (Map<String, Object>) TreePrompt.RESPONSE_SCHEMA.get("$defs");
        Map<String, Object> node = (Map<String, Object>) defs.get("node");
        Map<String, Object> properties = (Map<String, Object>) node.get("properties");

        assertThat(properties.keySet()).containsExactly("name", "memo", "children");
    }

    @Test
    @DisplayName("자식 배열은 maxItems 로 폭을 제한한다")
    @SuppressWarnings("unchecked")
    void childrenWidthIsLimited() {
        Map<String, Object> defs = (Map<String, Object>) TreePrompt.RESPONSE_SCHEMA.get("$defs");
        Map<String, Object> node = (Map<String, Object>) defs.get("node");
        Map<String, Object> properties = (Map<String, Object>) node.get("properties");
        Map<String, Object> children = (Map<String, Object>) properties.get("children");

        assertThat(children).containsEntry("maxItems", TreePrompt.MAX_CHILDREN);
    }

    @Test
    @DisplayName("시스템 프롬프트에 분량·상한 값이 치환되어 들어간다")
    void systemPromptContainsResolvedLimits() {
        assertThat(TreePrompt.SYSTEM)
            .contains(TreePrompt.DEFAULT_MIN_NODES + "~" + TreePrompt.DEFAULT_MAX_NODES + "개")
            .contains("20%를")
            .contains(TreePrompt.MAX_NODES + "개를 넘으면").contains("최대 " + TreePrompt.MAX_CHILDREN + "개")
            .contains(TreePrompt.MAX_DEPTH + "단계를 넘지")
            .doesNotContain("%d");
    }
}

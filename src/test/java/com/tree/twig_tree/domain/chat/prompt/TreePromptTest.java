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
}

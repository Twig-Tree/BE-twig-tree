package com.tree.twig_tree.domain.lecture.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프론트엔드에서 복사해 온 리소스가 쓸 수 있는 상태인지 확인한다.
 *
 * <p>두 파일은 프론트엔드의 코드 생성물이므로, 한쪽만 복사했거나 낡은 복사본이 들어왔을 때
 * 모델을 부르기 전에 여기서 걸리게 한다.
 */
class LectureScenarioResourcesTest {

    private LectureScenarioResources resources;

    @BeforeEach
    void setUp() {
        resources = new LectureScenarioResources(JsonMapper.builder().build());
    }

    @Test
    @DisplayName("두 리소스를 읽고 스키마 버전을 노출한다")
    void loadsResources() {
        assertThat(resources.scenarioSchemaVersion()).isPositive();
        assertThat(resources.strictSchema()).isNotNull();
        assertThat(resources.vocabularyJson()).isNotBlank();
    }

    @Test
    @DisplayName("strict 스키마와 어휘 파일의 스키마 버전이 같다")
    void versionsMatch() {
        JsonNode schemaVersion = resources.strictSchema()
            .path("properties").path("scenarioSchemaVersion").path("enum");

        assertThat(schemaVersion.get(0).asInt()).isEqualTo(resources.scenarioSchemaVersion());
    }

    @Test
    @DisplayName("strict 스키마는 strict 모드가 요구하는 모양을 갖춘다")
    void strictSchemaShape() {
        JsonNode schema = resources.strictSchema();

        assertThat(schema.path("type").asString()).isEqualTo("object");
        assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
        assertThat(schema.path("$defs").isObject()).isTrue();
        assertThat(schema.path("properties").path("scenes").isObject()).isTrue();
    }

    @Test
    @DisplayName("어휘 파일에 모델이 고를 값과 규칙이 들어 있다")
    void vocabularyContent() {
        JsonNode vocabulary = JsonMapper.builder().build().readTree(resources.vocabularyJson());

        assertThat(vocabulary.path("intents").isArray()).isTrue();
        assertThat(vocabulary.path("intents")).isNotEmpty();
        assertThat(vocabulary.path("delivery").isArray()).isTrue();
        assertThat(vocabulary.path("rules")).isNotEmpty();
    }
}

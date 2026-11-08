package com.tree.twig_tree.domain.lecture.service;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 강의 시나리오 생성에 쓰는 프론트엔드 생성물 두 개를 담는다.
 *
 * <p>출처는 {@code twig-tree-web} 의 {@code docs/specs/} 이고, 둘 다 프론트엔드의 코드 생성물이므로
 * 백엔드에서 손으로 고치지 않는다. 프론트엔드에서 시나리오 스키마나 어휘가 바뀌면 파일을 다시 복사한다.
 *
 * <ul>
 *   <li>{@code lecture-scenario.strict.schema.json} — OpenAI strict 모드에 그대로 넘기는 JSON Schema</li>
 *   <li>{@code lecture-generation-vocabulary.json} — system 프롬프트에 그대로 싣는 어휘와 규칙</li>
 * </ul>
 *
 * <p>두 파일의 {@code scenarioSchemaVersion} 이 다르면 한쪽만 복사된 상태이므로 기동을 멈춘다.
 * 어긋난 스키마로 모델을 부르지 않기 위해서다.
 */
@Component
public class LectureScenarioResources {

    private static final String SCHEMA_PATH = "lecture/lecture-scenario.strict.schema.json";
    private static final String VOCABULARY_PATH = "lecture/lecture-generation-vocabulary.json";

    private static final String SCHEMA_VERSION_FIELD = "scenarioSchemaVersion";

    private final JsonNode strictSchema;
    private final String vocabularyJson;
    private final int scenarioSchemaVersion;

    public LectureScenarioResources(ObjectMapper objectMapper) {
        this.strictSchema = readJson(objectMapper, SCHEMA_PATH);
        this.vocabularyJson = readText(VOCABULARY_PATH);

        JsonNode vocabulary = objectMapper.readTree(vocabularyJson);
        this.scenarioSchemaVersion = requireVersion(vocabulary, VOCABULARY_PATH);

        int schemaVersion = requireSchemaVersion();
        if (schemaVersion != scenarioSchemaVersion) {
            throw new IllegalStateException(
                "강의 시나리오 리소스의 scenarioSchemaVersion 이 다릅니다: 스키마 %d, 어휘 %d"
                    .formatted(schemaVersion, scenarioSchemaVersion));
        }
    }

    /** strict 모드에 넘길 JSON Schema. */
    public JsonNode strictSchema() {
        return strictSchema;
    }

    /** system 프롬프트에 실을 어휘 파일 전문. */
    public String vocabularyJson() {
        return vocabularyJson;
    }

    /** 서버가 가진 시나리오 스키마 버전. 요청의 값과 비교한다. */
    public int scenarioSchemaVersion() {
        return scenarioSchemaVersion;
    }

    /**
     * strict 스키마의 {@code scenarioSchemaVersion} 은 값 하나짜리 enum 으로 들어 있다.
     * (strict 모드가 {@code const} 를 받지 않아 프론트엔드가 enum 으로 바꿔 둔다)
     */
    private int requireSchemaVersion() {
        JsonNode versionEnum = strictSchema
            .path("properties").path(SCHEMA_VERSION_FIELD).path("enum");

        if (!versionEnum.isArray() || versionEnum.isEmpty() || !versionEnum.get(0).isNumber()) {
            throw new IllegalStateException(
                "%s 에서 %s 를 읽을 수 없습니다.".formatted(SCHEMA_PATH, SCHEMA_VERSION_FIELD));
        }
        return versionEnum.get(0).asInt();
    }

    private static int requireVersion(JsonNode root, String path) {
        JsonNode version = root.path(SCHEMA_VERSION_FIELD);
        if (!version.isNumber()) {
            throw new IllegalStateException(
                "%s 에서 %s 를 읽을 수 없습니다.".formatted(path, SCHEMA_VERSION_FIELD));
        }
        return version.asInt();
    }

    private static JsonNode readJson(ObjectMapper objectMapper, String classpath) {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            return objectMapper.readTree(is);
        } catch (IOException e) {
            throw new IllegalStateException("강의 시나리오 리소스를 읽을 수 없습니다: " + classpath, e);
        }
    }

    private static String readText(String classpath) {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("강의 시나리오 리소스를 읽을 수 없습니다: " + classpath, e);
        }
    }
}

package com.tree.twig_tree.domain.chat.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * mock 트리 데이터가 실제 저장 규칙과 어긋나지 않는지 확인한다.
 *
 * <p>mock 은 DB 를 거치지 않아 제약을 위반해도 응답이 나간다. 프론트가 실제 API 로 넘어갈 때
 * 형태가 달라지는 것을 막으려면 mock 쪽에서 미리 맞춰둬야 한다.
 */
class MockTreeDataTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest(name = "tree-{0}.json")
    @ValueSource(strings = {"empty", "small", "large", "max"})
    @DisplayName("mock 트리의 루트는 최대 하나다 — uk_nodes_root_per_tree 와 같은 규칙")
    void atMostOneRoot(String scenario) {
        JsonNode nodes = load(scenario).get("nodes");

        long rootCount = 0;
        for (JsonNode node : nodes) {
            if (node.get("parentId").isNull()) {
                rootCount++;
            }
        }

        assertThat(rootCount)
            .as("tree-%s.json 의 루트 노드 수", scenario)
            .isLessThanOrEqualTo(1);
    }

    @ParameterizedTest(name = "tree-{0}.json")
    @ValueSource(strings = {"small", "large", "max"})
    @DisplayName("루트가 아닌 노드의 parentId 는 같은 파일 안에 존재한다")
    void parentIdsResolve(String scenario) {
        JsonNode nodes = load(scenario).get("nodes");

        var ids = new java.util.HashSet<Long>();
        for (JsonNode node : nodes) {
            ids.add(node.get("nodeId").asLong());
        }

        for (JsonNode node : nodes) {
            JsonNode parentId = node.get("parentId");
            if (!parentId.isNull()) {
                assertThat(ids)
                    .as("tree-%s.json 의 nodeId=%s 가 참조하는 parentId", scenario, node.get("nodeId"))
                    .contains(parentId.asLong());
            }
        }
    }

    private JsonNode load(String scenario) {
        try (InputStream in = new ClassPathResource("mocks/chat/tree-" + scenario + ".json").getInputStream()) {
            return mapper.readTree(in);
        } catch (Exception e) {
            throw new IllegalStateException("mock 파일을 읽지 못했습니다: " + scenario, e);
        }
    }
}

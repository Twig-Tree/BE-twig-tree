package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.prompt.TreePrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreeValidatorTest {

    private final TreeValidator validator = new TreeValidator();

    private LlmNode node(String name, LlmNode... children) {
        return new LlmNode(name, null, List.of(children));
    }

    /** 자식이 하나씩 이어진 일자형 트리. */
    private LlmTreeDTO chain(int depth) {
        LlmNode current = node("노드" + depth);
        for (int level = depth - 1; level >= 1; level--) {
            current = node("노드" + level, current);
        }
        return new LlmTreeDTO(current);
    }

    private LlmTreeDTO flat(int nodeCount) {
        List<LlmNode> children = IntStream.range(1, nodeCount)
            .mapToObj(i -> node("자식" + i))
            .toList();
        return new LlmTreeDTO(new LlmNode("루트", null, children));
    }

    @Test
    @DisplayName("정상 트리는 통과한다")
    void validTree() {
        assertValid(new LlmTreeDTO(node("루트", node("자식1", node("손자")), node("자식2"))));
    }

    @Test
    @DisplayName("null 트리는 LLM_RESPONSE_INVALID")
    void nullTree() {
        assertInvalid(null);
    }

    @Test
    @DisplayName("루트가 없으면 실패한다")
    void noRoot() {
        assertInvalid(new LlmTreeDTO(null));
    }

    @Test
    @DisplayName("이름이 비어 있으면 실패한다")
    void blankName() {
        assertInvalid(new LlmTreeDTO(node("  ")));
    }

    @Test
    @DisplayName("하위 노드의 이름이 비어 있어도 실패한다")
    void blankNameInDescendant() {
        assertInvalid(new LlmTreeDTO(node("루트", node("자식", node("")))));
    }

    @Test
    @DisplayName("children 배열에 null 이 있으면 실패한다")
    void nullChild() {
        List<LlmNode> children = new ArrayList<>(Arrays.asList(node("자식"), null));

        assertInvalid(new LlmTreeDTO(new LlmNode("루트", null, children)));
    }

    @Test
    @DisplayName("children 을 생략하면 리프 노드로 다룬다 — 스키마를 강제하지 못하는 제공자 대비")
    void missingChildrenIsLeaf() {
        assertValid(new LlmTreeDTO(new LlmNode("루트", null, null)));
    }

    @Test
    @DisplayName("이름이 상한(30자)과 같으면 통과한다 — nodes.name VARCHAR(30)")
    void nameAtLimit() {
        assertValid(new LlmTreeDTO(node("가".repeat(30))));
    }

    @Test
    @DisplayName("이름이 상한(30자)을 넘으면 저장 전에 실패한다")
    void nameOverLimit() {
        assertInvalid(new LlmTreeDTO(node("가".repeat(31))));
    }

    @Test
    @DisplayName("메모가 상한(500자)과 같으면 통과한다 — nodes.memo VARCHAR(500)")
    void memoAtLimit() {
        assertValid(new LlmTreeDTO(new LlmNode("루트", "가".repeat(500), List.of())));
    }

    @Test
    @DisplayName("메모가 상한(500자)을 넘으면 저장 전에 실패한다")
    void memoOverLimit() {
        assertInvalid(new LlmTreeDTO(new LlmNode("루트", "가".repeat(501), List.of())));
    }

    @Test
    @DisplayName("자식이 하나뿐인 노드도 통과한다")
    void singleChildIsAccepted() {
        assertValid(new LlmTreeDTO(node("루트", node("유일한 자식"))));
    }

    @Test
    @DisplayName("깊이 상한(100단계)과 같으면 통과한다 — 루트가 1단계")
    void depthAtLimit() {
        assertValid(chain(TreePrompt.MAX_DEPTH));
    }

    @Test
    @DisplayName("깊이 상한(100단계)을 넘으면 실패한다 — 재귀 스키마는 깊이를 막지 못한다")
    void depthOverLimit() {
        assertInvalid(chain(TreePrompt.MAX_DEPTH + 1));
    }

    @Test
    @DisplayName("상한 깊이의 중첩 JSON 도 파싱되어 검증을 통과한다 — 파서의 중첩 제한에 걸리지 않게")
    void deepestJsonParsesAndValidates() {
        StringBuilder json = new StringBuilder("{\"root\":");
        for (int level = 1; level <= TreePrompt.MAX_DEPTH; level++) {
            json.append("{\"name\":\"노드\",\"memo\":null,\"children\":[");
        }
        json.append("]}".repeat(TreePrompt.MAX_DEPTH)).append('}');

        LlmTreeDTO tree = JsonMapper.builder().build().readValue(json.toString(), LlmTreeDTO.class);

        assertValid(tree);
    }

    @Test
    @DisplayName("얕은 트리는 그대로 통과한다 — 깊이는 상한일 뿐 목표치가 아니다")
    void shallowTreeIsAccepted() {
        assertValid(new LlmTreeDTO(node("루트", node("자식1"), node("자식2"))));
    }

    @Test
    @DisplayName("노드 수 상한(100)과 같으면 통과한다")
    void nodesAtLimit() {
        assertValid(flat(TreePrompt.MAX_NODES));
    }

    @Test
    @DisplayName("노드 수 상한(100)을 초과하면 실패한다")
    void tooManyNodes() {
        assertInvalid(flat(TreePrompt.MAX_NODES + 1));
    }

    private void assertValid(LlmTreeDTO tree) {
        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    private void assertInvalid(LlmTreeDTO tree) {
        assertThatThrownBy(() -> validator.validate(tree))
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.LLM_RESPONSE_INVALID));
    }
}

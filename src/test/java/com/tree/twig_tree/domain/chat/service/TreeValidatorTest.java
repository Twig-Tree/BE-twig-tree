package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreeValidatorTest {

    private final TreeValidator validator = new TreeValidator();

    private LlmNode node(Long tempId, Long parentTempId, Long orderId) {
        return new LlmNode(tempId, "이름" + tempId, null, parentTempId, orderId);
    }

    @Test
    @DisplayName("정상 트리는 통과한다")
    void validTree() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 1L, 1L),
            node(3L, 1L, 2L)
        ));

        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("노드가 비어 있으면 LLM_RESPONSE_INVALID")
    void emptyNodes() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of());
        assertInvalid(tree);
    }

    @Test
    @DisplayName("null 트리는 LLM_RESPONSE_INVALID")
    void nullTree() {
        assertInvalid(null);
    }

    @Test
    @DisplayName("tempId 가 중복되면 실패한다")
    void duplicateTempId() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(1L, 1L, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("존재하지 않는 parentTempId 를 참조하면 실패한다")
    void unknownParent() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 99L, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("루트가 없으면 실패한다")
    void noRoot() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, 2L, 1L),
            node(2L, 1L, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("루트가 둘 이상이면 실패한다 — DB 는 트리당 루트 하나만 허용한다")
    void multipleRoots() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, null, 2L),
            node(3L, 1L, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("자기 자신을 부모로 지정하면 실패한다")
    void selfReference() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 2L, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("순환 참조가 있으면 실패한다")
    void cycle() {
        // 1(root) 아래 2<->3 순환
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 3L, 1L),
            node(3L, 2L, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("이름이 비어 있으면 실패한다")
    void blankName() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            new LlmNode(1L, "  ", null, null, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("orderId 가 없으면 실패한다")
    void nullOrderId() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            new LlmNode(1L, "루트", null, null, null)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("같은 부모 아래 orderId 가 겹치면 실패한다 — uk_nodes_parent_order_id")
    void duplicateSiblingOrderId() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 1L, 1L),
            node(3L, 1L, 1L)   // 2번과 형제인데 orderId 가 같다
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("부모가 다르면 orderId 가 같아도 통과한다")
    void sameOrderIdUnderDifferentParents() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 1L, 1L),
            node(3L, 1L, 2L),
            node(4L, 2L, 1L),  // 2번의 자식
            node(5L, 3L, 1L)   // 3번의 자식 — 4번과 형제가 아니므로 orderId 중복이 아니다
        ));

        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("루트는 orderId 중복 검사 대상이 아니다 — 인덱스가 parent_id IS NOT NULL 조건부다")
    void rootIsExemptFromOrderIdCheck() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 1L, 1L)   // 루트와 orderId 가 같지만 형제가 아니다
        ));

        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이름이 상한(30자)과 같으면 통과한다 — nodes.name VARCHAR(30)")
    void nameAtLimit() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            new LlmNode(1L, "가".repeat(30), null, null, 1L)
        ));
        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("이름이 상한(30자)을 넘으면 저장 전에 실패한다")
    void nameOverLimit() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            new LlmNode(1L, "가".repeat(31), null, null, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("메모가 상한(500자)과 같으면 통과한다 — nodes.memo VARCHAR(500)")
    void memoAtLimit() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            new LlmNode(1L, "루트", "가".repeat(500), null, 1L)
        ));
        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("메모가 상한(500자)을 넘으면 저장 전에 실패한다")
    void memoOverLimit() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            new LlmNode(1L, "루트", "가".repeat(501), null, 1L)
        ));
        assertInvalid(tree);
    }

    @Test
    @DisplayName("깊이 상한(4단계)과 같으면 통과한다 — 루트가 1단계")
    void depthAtLimit() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),   // 1단계
            node(2L, 1L, 1L),     // 2단계
            node(3L, 2L, 1L),     // 3단계
            node(4L, 3L, 1L)      // 4단계
        ));

        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("깊이 상한(4단계)을 넘으면 실패한다 — 프롬프트가 지켜지지 않아도 저장되지 않게")
    void depthOverLimit() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 1L, 1L),
            node(3L, 2L, 1L),
            node(4L, 3L, 1L),
            node(5L, 4L, 1L)      // 5단계
        ));

        assertInvalid(tree);
    }

    @Test
    @DisplayName("얕은 트리는 그대로 통과한다 — 깊이는 상한일 뿐 목표치가 아니다")
    void shallowTreeIsAccepted() {
        LlmTreeDTO tree = new LlmTreeDTO(List.of(
            node(1L, null, 1L),
            node(2L, 1L, 1L),
            node(3L, 1L, 2L)
        ));

        assertThatCode(() -> validator.validate(tree)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("노드 수 상한(100)을 초과하면 실패한다")
    void tooManyNodes() {
        List<LlmNode> nodes = IntStream.rangeClosed(1, 101)
            .mapToObj(i -> node((long) i, i == 1 ? null : 1L, (long) i))
            .toList();
        assertInvalid(new LlmTreeDTO(nodes));
    }

    private void assertInvalid(LlmTreeDTO tree) {
        assertThatThrownBy(() -> validator.validate(tree))
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode())
                .isEqualTo(ChatErrorCode.LLM_RESPONSE_INVALID));
    }
}

package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeMetricsTest {

    private static LlmNode node(String name, String memo, LlmNode... children) {
        return new LlmNode(name, memo, new ArrayList<>(Arrays.asList(children)));
    }

    private static TreeMetrics metrics(LlmNode root) {
        return TreeMetrics.from(new LlmTreeDTO(root));
    }

    @Test
    @DisplayName("트리나 루트가 없으면 모든 지표가 0 이다")
    void emptyTree() {
        TreeMetrics zero = new TreeMetrics(0, 0, 0, 0, 0, 0, 0);

        assertThat(TreeMetrics.from(null)).isEqualTo(zero);
        assertThat(TreeMetrics.from(new LlmTreeDTO(null))).isEqualTo(zero);
        assertThat(zero.singleChildRatio()).isZero();
        assertThat(zero.memoFillRate()).isZero();
    }

    @Test
    @DisplayName("노드 수, 깊이, 리프, 자식 1개 노드, memo 를 센다")
    void countsShape() {
        TreeMetrics result = metrics(node("자료구조", "개요",
            node("선형", null, node("스택", "LIFO")),
            node("비선형", " ")));

        assertThat(result.nodeCount()).isEqualTo(4);
        assertThat(result.maxDepth()).isEqualTo(3);
        assertThat(result.leafCount()).isEqualTo(2);
        assertThat(result.singleChildCount()).isEqualTo(1);
        assertThat(result.memoCount()).isEqualTo(2);
        assertThat(result.singleChildRatio()).isEqualTo(0.25);
        assertThat(result.memoFillRate()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("부모 이름 반복과 포장 접미사만 껍데기 후보로 센다")
    void shellCandidates() {
        TreeMetrics result = metrics(node("어패류", null,
            node("어패류 종류", null),
            node("어패류", null),
            node("어패류 손질법", null)));

        assertThat(result.shellCandidateCount()).isEqualTo(2);
        assertThat(metrics(node("트리", null, node("이진 트리", null))).shellCandidateCount()).isZero();
    }

    @Test
    @DisplayName("같은 부모 아래의 중복 이름만 공백과 대소문자를 무시하고 센다")
    void duplicateSiblings() {
        TreeMetrics siblings = metrics(node("루트", null,
            node("Hash Map", null), node("hashmap", null), node("큐", null)));
        TreeMetrics cousins = metrics(node("루트", null,
            node("가", null, node("정의", null)),
            node("나", null, node("정의", null))));

        assertThat(siblings.duplicateSiblingCount()).isEqualTo(1);
        assertThat(cousins.duplicateSiblingCount()).isZero();
    }

    @Test
    @DisplayName("검증 전 트리의 null 자식은 건너뛰고, 아주 깊은 트리도 측정한다")
    void toleratesUnvalidatedTrees() {
        assertThat(metrics(node("루트", null, (LlmNode) null)).nodeCount()).isEqualTo(1);

        LlmNode deep = new LlmNode("끝", null, List.of());
        for (int i = 0; i < 20_000; i++) {
            deep = new LlmNode("n" + i, null, List.of(deep));
        }
        assertThat(metrics(deep).maxDepth()).isEqualTo(20_001);
    }
}

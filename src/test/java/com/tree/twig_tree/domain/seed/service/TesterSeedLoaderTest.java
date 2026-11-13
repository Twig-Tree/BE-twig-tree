package com.tree.twig_tree.domain.seed.service;

import com.tree.twig_tree.domain.chat.service.TreeValidator;
import com.tree.twig_tree.domain.seed.dto.TesterSeedDTO;
import com.tree.twig_tree.domain.seed.dto.TesterSeedDTO.SeedNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TesterSeedLoaderTest {

    private final TreeValidator treeValidator = new TreeValidator();

    @Test
    @DisplayName("실제 시드 파일을 읽어 검증을 통과한다")
    void loadsBundledSeed() {
        TesterSeedLoader loader = new TesterSeedLoader(JsonMapper.builder().build(), treeValidator);

        TesterSeedDTO seed = loader.getSeed();
        assertThat(seed.workspaceName()).isEqualTo("고구려");
        assertThat(seed.tags()).hasSize(7);
        assertThat(seed.nodesInBfsOrder()).hasSize(14);
    }

    @Test
    @DisplayName("tags 목록에 없는 태그를 노드에 붙이면 실패한다")
    void rejectsUndefinedTag() {
        TesterSeedDTO seed = seed(List.of("인물"), node("루트", List.of("왕")));

        assertThatThrownBy(() -> TesterSeedLoader.validate(seed, treeValidator))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tag=왕");
    }

    @Test
    @DisplayName("같은 태그를 두 번 정의하면 실패한다")
    void rejectsDuplicatedTagDefinition() {
        TesterSeedDTO seed = seed(List.of("인물", "인물"), node("루트", List.of()));

        assertThatThrownBy(() -> TesterSeedLoader.validate(seed, treeValidator))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("같은 노드에 같은 태그를 두 번 붙이면 실패한다")
    void rejectsDuplicatedNodeTag() {
        TesterSeedDTO seed = seed(List.of("인물"), node("루트", List.of("인물", "인물")));

        assertThatThrownBy(() -> TesterSeedLoader.validate(seed, treeValidator))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("노드당 태그 상한(10개)을 넘기면 실패한다")
    void rejectsTooManyTagsOnNode() {
        List<String> tags = IntStream.rangeClosed(1, 11).mapToObj(i -> "태그" + i).toList();
        TesterSeedDTO seed = seed(tags, node("루트", tags));

        assertThatThrownBy(() -> TesterSeedLoader.validate(seed, treeValidator))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("30자를 넘는 태그 이름은 실패한다")
    void rejectsTooLongTagName() {
        TesterSeedDTO seed = seed(List.of(String.join("", Collections.nCopies(31, "가"))), node("루트", List.of()));

        assertThatThrownBy(() -> TesterSeedLoader.validate(seed, treeValidator))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("노드에 붙지 않은 태그가 있어도 통과한다")
    void allowsUnusedTag() {
        TesterSeedDTO seed = seed(List.of("인물", "복습 필요"), node("루트", List.of("인물")));

        assertThatCode(() -> TesterSeedLoader.validate(seed, treeValidator)).doesNotThrowAnyException();
    }

    private static TesterSeedDTO seed(List<String> tags, SeedNode root) {
        return new TesterSeedDTO("워크스페이스", tags, root);
    }

    private static SeedNode node(String name, List<String> tags) {
        return new SeedNode(name, null, tags, List.of());
    }
}

package com.tree.twig_tree.domain.seed.service;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.service.TreeValidator;
import com.tree.twig_tree.domain.seed.dto.TesterSeedDTO;
import com.tree.twig_tree.domain.seed.dto.TesterSeedDTO.SeedNode;
import com.tree.twig_tree.domain.tag.service.TagService;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 테스터 시드 트리를 앱 기동 시점에 한 번 읽고 검증한다. 시드가 잘못되어 있으면 앱이 뜨지 않는다.
 */
@Component
@ConditionalOnProperty(name = "tester-seed.enabled", havingValue = "true")
public class TesterSeedLoader {

    static final String SEED_PATH = "seed/tester-tree.json";
    private static final int WORKSPACE_NAME_MAX_LENGTH = 30;
    private static final int TAG_NAME_MAX_LENGTH = 30;

    @Getter
    private final TesterSeedDTO seed;

    public TesterSeedLoader(ObjectMapper objectMapper, TreeValidator treeValidator) {
        this.seed = load(objectMapper, new ClassPathResource(SEED_PATH));
        validate(seed, treeValidator);
    }

    static TesterSeedDTO load(ObjectMapper objectMapper, Resource resource) {
        try (InputStream is = resource.getInputStream()) {
            return objectMapper.readValue(is, TesterSeedDTO.class);
        } catch (IOException e) {
            throw new IllegalStateException("테스터 시드 파일을 읽을 수 없습니다: " + SEED_PATH, e);
        } catch (JacksonException e) {
            // Jackson 3 은 파싱·매핑 오류를 IOException 이 아닌 런타임 예외로 던진다
            throw new IllegalStateException("테스터 시드 파일의 JSON 이 올바르지 않습니다: " + SEED_PATH, e);
        }
    }

    static void validate(TesterSeedDTO seed, TreeValidator treeValidator) {
        String name = seed.workspaceName();
        if (name == null || name.isBlank() || name.length() > WORKSPACE_NAME_MAX_LENGTH) {
            throw new IllegalStateException(
                "테스터 시드의 워크스페이스 이름은 1~" + WORKSPACE_NAME_MAX_LENGTH + "자여야 합니다: " + name);
        }
        if (seed.root() == null) {
            throw new IllegalStateException("테스터 시드에 루트 노드가 없습니다: " + SEED_PATH);
        }
        try {
            // 노드 수·깊이·이름/메모 길이는 LLM 트리와 같은 기준으로 확인한다 (상세 사유는 검증기가 로그로 남긴다)
            treeValidator.validate(seed.toTree());
        } catch (ChatException e) {
            throw new IllegalStateException("테스터 시드 트리가 유효하지 않습니다: " + SEED_PATH, e);
        }
        validateTags(seed);
    }

    /** 태그 제약(이름 길이, 트리 안 이름 유일, 노드당 개수, 같은 노드 중복 금지)을 API 와 같은 기준으로 확인한다. */
    private static void validateTags(TesterSeedDTO seed) {
        Set<String> defined = new HashSet<>();
        for (String tag : seed.tags()) {
            if (tag == null || tag.isBlank() || tag.length() > TAG_NAME_MAX_LENGTH) {
                throw new IllegalStateException(
                    "테스터 시드의 태그 이름은 1~" + TAG_NAME_MAX_LENGTH + "자여야 합니다: " + tag);
            }
            if (!defined.add(tag)) {
                throw new IllegalStateException("테스터 시드에 같은 태그가 두 번 정의되어 있습니다: " + tag);
            }
        }

        for (SeedNode node : seed.nodesInBfsOrder()) {
            List<String> tags = node.tags();
            if (tags.size() > TagService.MAX_NODE_TAG_COUNT) {
                throw new IllegalStateException("노드당 태그는 " + TagService.MAX_NODE_TAG_COUNT
                    + "개까지 붙일 수 있습니다: node=" + node.name());
            }
            if (new HashSet<>(tags).size() != tags.size()) {
                throw new IllegalStateException("같은 노드에 같은 태그가 두 번 붙어 있습니다: node=" + node.name());
            }
            for (String tag : tags) {
                if (!defined.contains(tag)) {
                    throw new IllegalStateException(
                        "tags 목록에 없는 태그를 노드에 붙였습니다: node=" + node.name() + ", tag=" + tag);
                }
            }
        }
    }
}

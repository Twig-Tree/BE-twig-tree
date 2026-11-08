package com.tree.twig_tree.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * LLM 이 생성한 트리 JSON 을 파싱하기 위한 중간 DTO.
 *
 * <p>부모-자식은 중첩으로, 형제 순서는 {@code children} 의 배열 순서로 표현한다.
 * LLM 출력은 신뢰할 수 없는 외부 입력이므로 파싱 후 반드시 TreeValidator 로 검증한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmTreeDTO(
    LlmNode root
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmNode(
        String name,
        String memo,
        List<LlmNode> children
    ) {

        /** 스키마를 강제하지 못하는 제공자가 children 을 생략해도 리프 노드로 다룬다. */
        public LlmNode {
            children = children == null ? List.of() : children;
        }
    }
}

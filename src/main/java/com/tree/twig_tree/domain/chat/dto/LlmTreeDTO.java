package com.tree.twig_tree.domain.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * LLM 이 생성한 트리 JSON 을 파싱하기 위한 중간 DTO.
 *
 * <p>실제 nodeId/parentId 는 저장 시 발급되므로, LLM 은 tempId/parentTempId 로 관계를 표현한다.
 * LLM 출력은 신뢰할 수 없는 외부 입력이므로 파싱 후 반드시 TreeValidator 로 검증한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmTreeDTO(
    List<LlmNode> nodes
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LlmNode(
        Long tempId,
        String name,
        String memo,
        Long parentTempId,
        Long orderId
    ) {}
}

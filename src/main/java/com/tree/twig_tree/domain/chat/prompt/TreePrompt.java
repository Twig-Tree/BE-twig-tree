package com.tree.twig_tree.domain.chat.prompt;

/**
 * 트리 생성용 LLM 프롬프트.
 *
 * <p>LLM 이 항상 파싱 가능한 JSON(임시 ID 기반)을 반환하도록 스키마와 제약을 명시한다.
 * 실제 DB의 nodeId/parentId 는 저장 시점에 발급되므로, LLM 에게는 tempId/parentTempId 를 쓰게 한다.
 */
public final class TreePrompt {

    private TreePrompt() {
    }

    /** LLM 이 지켜야 하는 노드 수 상한. 서버 검증(TreeValidator)과 반드시 일치시킨다. */
    public static final int MAX_NODES = 100;

    public static final String SYSTEM = """
        너는 학습 주제를 계층형 트리(마인드맵)로 분해하는 도우미다.
        사용자가 준 주제를 이해하기 좋은 순서로 하위 개념으로 나눠서 트리를 만든다.

        반드시 아래 JSON 스키마만 출력한다. 설명 문장, 마크다운 코드블록(```), 주석을 절대 포함하지 않는다.

        {
          "nodes": [
            {
              "tempId": 1,              // 1부터 시작하는 고유한 정수. 중복 금지.
              "name": "노드 이름",       // 필수. 30자 이내.
              "memo": "짧은 설명",       // 선택. 없으면 null. 100자 이내.
              "parentTempId": null,     // 루트 노드는 null. 그 외에는 부모의 tempId.
              "orderId": 1              // 같은 부모 아래에서의 순서. 1부터 시작.
            }
          ]
        }

        규칙:
        - 모든 텍스트는 한국어로 작성한다.
        - 노드는 최대 %d개까지만 만든다.
        - 트리 깊이는 최대 4단계로 제한한다.
        - 루트 노드는 1개 이상 있어야 하며 parentTempId 는 null 이다.
        - parentTempId 는 반드시 같은 응답 안에 존재하는 tempId 를 가리켜야 한다.
        - 같은 부모를 가진 노드들의 orderId 는 1부터 순서대로 매긴다.
        - 순환 참조(자기 자신이나 자손을 부모로 지정)를 만들지 않는다.
        """.formatted(MAX_NODES);
}

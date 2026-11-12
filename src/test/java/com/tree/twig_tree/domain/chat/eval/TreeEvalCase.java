package com.tree.twig_tree.domain.chat.eval;

/**
 * 평가 세트의 케이스 하나. {@code tree-eval/cases.json} 에 정의한다.
 *
 * @param id            케이스 식별자
 * @param category      집계 단위
 * @param message       사용자 지시문. 문서만 넣는 케이스는 {@code null}
 * @param documentFile  {@code tree-eval/docs/} 아래의 입력 문서. 없으면 {@code null}
 * @param expectedDepth 지켜야 하는 최대 깊이. 판정하지 않으면 {@code null}
 * @param minNodes      노드 수 하한. 판정하지 않으면 {@code null}
 * @param maxNodes      노드 수 상한. 판정하지 않으면 {@code null}
 */
public record TreeEvalCase(
    String id,
    String category,
    String message,
    String documentFile,
    Integer expectedDepth,
    Integer minNodes,
    Integer maxNodes
) {
}

package com.tree.twig_tree.domain.chat.eval;

/**
 * LLM 채점 결과. 자동 지표로 잴 수 없는 의미 품질을 근사한다.
 *
 * @param mece                형제 노드가 겹치지 않고 빠짐없이 나뉘었는지 (1~5)
 * @param granularity         같은 층의 추상화 수준이 일관적인지 (1~5)
 * @param ordering            형제 순서가 학습 흐름에 맞는지 (1~5)
 * @param shellNodeCount      부모와 사실상 같은 뜻인 노드 수
 * @param ungroundedNodeCount 입력 문서에 근거가 없는 노드 수. 문서 입력이 아니면 {@code null}
 * @param comment             가장 큰 문제 한 줄
 */
public record TreeEvalJudgement(
    int mece,
    int granularity,
    int ordering,
    int shellNodeCount,
    Integer ungroundedNodeCount,
    String comment
) {

    TreeEvalJudgement withoutGrounding() {
        return new TreeEvalJudgement(mece, granularity, ordering, shellNodeCount, null, comment);
    }
}

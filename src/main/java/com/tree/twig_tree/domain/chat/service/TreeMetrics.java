package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO;
import com.tree.twig_tree.domain.chat.dto.LlmTreeDTO.LlmNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * LLM 이 생성한 트리의 모양을 숫자로 요약한 값.
 *
 * <p>운영 로그와 평가 하네스가 같은 계산을 쓴다. 검증에서 거부될 트리도 측정할 수 있도록
 * 노드 수·깊이에 상한을 두지 않고, null 자식은 세지 않고 건너뛴다.
 *
 * @param nodeCount             전체 노드 수
 * @param maxDepth              최대 깊이. 루트가 1단계
 * @param leafCount             자식이 없는 노드 수
 * @param singleChildCount      자식이 정확히 1개인 노드 수. 일자형 체인의 지표
 * @param shellCandidateCount   부모와 이름이 같거나 부모 이름에 "종류" 같은 말만 붙은 노드 수. 이름만 보는 근사치다
 * @param duplicateSiblingCount 같은 부모 아래에서 이름이 겹치는 노드 수. 처음 나온 노드는 세지 않는다
 * @param memoCount             memo 가 채워진 노드 수
 */
public record TreeMetrics(
    int nodeCount,
    int maxDepth,
    int leafCount,
    int singleChildCount,
    int shellCandidateCount,
    int duplicateSiblingCount,
    int memoCount
) {

    private static final List<String> WRAPPER_SUFFIXES = List.of("종류", "유형", "분류", "구분", "목록", "개요");

    public static TreeMetrics from(LlmTreeDTO tree) {
        if (tree == null || tree.root() == null) {
            return new TreeMetrics(0, 0, 0, 0, 0, 0, 0);
        }

        int nodes = 0;
        int maxDepth = 0;
        int leaves = 0;
        int singleChild = 0;
        int shellCandidates = 0;
        int duplicateSiblings = 0;
        int memos = 0;

        // 검증 전의 트리는 깊이를 믿을 수 없어 재귀 대신 명시적 스택으로 순회한다.
        Deque<NodeAtDepth> stack = new ArrayDeque<>();
        stack.push(new NodeAtDepth(tree.root(), 1));

        while (!stack.isEmpty()) {
            NodeAtDepth current = stack.pop();
            LlmNode node = current.node();

            nodes++;
            maxDepth = Math.max(maxDepth, current.depth());
            if (node.memo() != null && !node.memo().isBlank()) {
                memos++;
            }

            List<LlmNode> children = node.children().stream().filter(c -> c != null).toList();
            if (children.isEmpty()) {
                leaves++;
            } else if (children.size() == 1) {
                singleChild++;
            }

            String parentName = normalize(node.name());
            Set<String> siblingNames = new HashSet<>();
            for (LlmNode child : children) {
                String childName = normalize(child.name());
                if (isShellOf(childName, parentName)) {
                    shellCandidates++;
                }
                if (!childName.isEmpty() && !siblingNames.add(childName)) {
                    duplicateSiblings++;
                }
                stack.push(new NodeAtDepth(child, current.depth() + 1));
            }
        }

        return new TreeMetrics(nodes, maxDepth, leaves, singleChild, shellCandidates, duplicateSiblings, memos);
    }

    /** 자식이 1개인 노드의 비율. 노드가 없으면 0. */
    public double singleChildRatio() {
        return nodeCount == 0 ? 0 : (double) singleChildCount / nodeCount;
    }

    /** memo 가 채워진 노드의 비율. 노드가 없으면 0. */
    public double memoFillRate() {
        return nodeCount == 0 ? 0 : (double) memoCount / nodeCount;
    }

    /**
     * 자식 이름이 부모 이름을 그대로 반복하는지 본다.
     *
     * <p>"트리 &gt; 이진 트리" 처럼 부모 이름을 포함한 정상 노드가 흔해 단순 포함 관계로는 판단하지 않는다.
     */
    private static boolean isShellOf(String childName, String parentName) {
        if (childName.isEmpty() || parentName.isEmpty()) {
            return false;
        }
        return childName.equals(parentName)
            || WRAPPER_SUFFIXES.stream().anyMatch(suffix -> childName.equals(parentName + suffix));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record NodeAtDepth(LlmNode node, int depth) {
    }
}

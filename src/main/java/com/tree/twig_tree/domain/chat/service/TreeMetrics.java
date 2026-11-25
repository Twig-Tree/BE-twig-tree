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
 * @param genericCandidateCount "개요", "특징", "장단점"처럼 어느 주제에나 붙는 이름의 노드 수. 부모 이름을 앞에 붙인
 *                              경우("스택의 특징")도 센다. 정해 둔 목록만 보는 근사치다
 * @param duplicateSiblingCount 같은 부모 아래에서 이름이 겹치는 노드 수. 처음 나온 노드는 세지 않는다
 * @param memoCount             memo 가 채워진 노드 수
 */
public record TreeMetrics(
    int nodeCount,
    int maxDepth,
    int leafCount,
    int singleChildCount,
    int shellCandidateCount,
    int genericCandidateCount,
    int duplicateSiblingCount,
    int memoCount
) {

    private static final List<String> WRAPPER_SUFFIXES = List.of("종류", "유형", "분류", "구분", "목록", "개요");

    /**
     * 이름만 보고는 무엇을 배우는지 알 수 없는 범용 노드 이름. 공백을 뺀 형태로 비교한다.
     *
     * <p>"역사", "원리"처럼 주제에 따라 정상적인 대분류가 되는 말은 넣지 않는다. 목록 밖의 표현은 LLM 채점이 잡는다.
     */
    private static final Set<String> GENERIC_NAMES = Set.of(
        "개요", "정의", "개념", "기본개념", "핵심개념", "주요개념",
        "특징", "주요특징", "장단점", "장점", "단점",
        "활용", "활용사례", "응용", "사례", "예시",
        "중요성", "기초", "심화", "정리", "기타"
    );

    /** 부모 이름 뒤에 붙는 관형격 조사. "스택의 특징"을 "특징"과 같이 본다. */
    private static final String POSSESSIVE = "의";

    public static TreeMetrics from(LlmTreeDTO tree) {
        if (tree == null || tree.root() == null) {
            return new TreeMetrics(0, 0, 0, 0, 0, 0, 0, 0);
        }

        int nodes = 0;
        int maxDepth = 0;
        int leaves = 0;
        int singleChild = 0;
        int shellCandidates = 0;
        int genericCandidates = isGenericName(normalize(tree.root().name()), "") ? 1 : 0;
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
                if (isGenericName(childName, parentName)) {
                    genericCandidates++;
                }
                if (!childName.isEmpty() && !siblingNames.add(childName)) {
                    duplicateSiblings++;
                }
                stack.push(new NodeAtDepth(child, current.depth() + 1));
            }
        }

        return new TreeMetrics(nodes, maxDepth, leaves, singleChild, shellCandidates, genericCandidates,
            duplicateSiblings, memos);
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

    /**
     * 이름이 범용 노드 이름인지 본다. 부모 이름을 앞에 붙인 경우는 그 부분을 떼고 본다.
     *
     * <p>"어패류 개요"처럼 껍데기 후보이면서 범용 이름인 노드는 두 지표에 모두 센다. 껍데기는 구조가 비었는지,
     * 범용 이름은 내용이 구체적인지를 보는 지표라 서로 겹칠 수 있다.
     */
    private static boolean isGenericName(String name, String parentName) {
        if (name.isEmpty()) {
            return false;
        }
        if (GENERIC_NAMES.contains(name)) {
            return true;
        }
        if (parentName.isEmpty() || !name.startsWith(parentName)) {
            return false;
        }
        String rest = name.substring(parentName.length());
        if (rest.startsWith(POSSESSIVE)) {
            rest = rest.substring(POSSESSIVE.length());
        }
        return GENERIC_NAMES.contains(rest);
    }

    private static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private record NodeAtDepth(LlmNode node, int depth) {
    }
}

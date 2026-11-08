package com.tree.twig_tree.domain.chat.eval;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 클래스패스의 평가 세트를 읽는다. */
final class TreeEvalCases {

    private static final String CASES_PATH = "tree-eval/cases.json";
    private static final String DOCS_PATH = "tree-eval/docs/";

    private TreeEvalCases() {
    }

    static List<TreeEvalCase> load(ObjectMapper mapper) {
        try (InputStream in = open(CASES_PATH)) {
            return List.of(mapper.readValue(in, TreeEvalCase[].class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 케이스의 입력 문서 본문. 문서가 없는 케이스면 {@code null}. */
    static String document(TreeEvalCase evalCase) {
        if (evalCase.documentFile() == null) {
            return null;
        }
        try (InputStream in = open(DOCS_PATH + evalCase.documentFile())) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static InputStream open(String path) {
        InputStream in = TreeEvalCases.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("평가 리소스를 찾지 못했습니다: " + path);
        }
        return in;
    }
}

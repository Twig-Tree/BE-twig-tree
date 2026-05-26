package com.tree.twig_tree.global.mock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * classpath 의 mock JSON 파일을 읽어 {@link JsonNode} 로 반환한다.
 *
 * <p>경로 예시: {@code mocks/chat/tree-default.json}
 */
@Component
@RequiredArgsConstructor
public class MockResponseLoader {

    private final ObjectMapper objectMapper;

    public JsonNode load(String classpath) {
        try (InputStream is = new ClassPathResource(classpath).getInputStream()) {
            return objectMapper.readTree(is);
        } catch (Exception e) {
            throw new MockNotFoundException("Mock 파일을 찾을 수 없습니다: " + classpath, e);
        }
    }

    public static class MockNotFoundException extends RuntimeException {
        public MockNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package com.tree.twig_tree.domain.workspace.dto;

import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 커서 인코딩/디코딩. 왕복 시 정렬 키가 손실 없이 복원되고, 깨진 커서는 INVALID_CURSOR 여야 한다.
 */
class WorkspaceCursorTest {

    @Test
    @DisplayName("encode 한 커서를 decode 하면 같은 값이 복원된다 (마이크로초 포함)")
    void roundTrip() {
        // DB(timestamptz)는 마이크로초까지 저장하므로 그 정밀도가 보존되어야 같은 항목이 다시 조회되지 않는다
        Instant updatedAt = Instant.parse("2026-10-03T08:12:30.123456Z");
        WorkspaceCursor cursor = new WorkspaceCursor(updatedAt, 12L);

        WorkspaceCursor decoded = WorkspaceCursor.decode(cursor.encode());

        assertThat(decoded).isEqualTo(cursor);
    }

    @Test
    @DisplayName("인코딩 결과는 URL 에 그대로 넣을 수 있다 (+, /, = 없음)")
    void urlSafe() {
        String encoded = new WorkspaceCursor(Instant.parse("2026-10-03T08:12:30.123456Z"), 12L).encode();

        assertThat(encoded).doesNotContain("+", "/", "=");
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @DisplayName("형식이 잘못된 커서는 INVALID_CURSOR")
    @ValueSource(strings = {
            "%%%",          // Base64 디코딩 실패
            "abc",          // 디코딩은 되지만 구분자가 없음
    })
    void invalidEncoding(String cursor) {
        assertInvalidCursor(cursor);
    }

    @ParameterizedTest(name = "[{index}] \"{0}\"")
    @DisplayName("디코딩된 내용이 잘못된 커서는 INVALID_CURSOR")
    @ValueSource(strings = {
            "not-a-date_12",                        // 날짜 파싱 실패 (DateTimeParseException)
            "2026-10-03T08:12:30Z_abc",             // id 파싱 실패 (NumberFormatException)
            "2026-10-03T08:12:30Z_12_34",           // 구분자 과다
            "2026-10-03T08:12:30Z_",                // id 누락
    })
    void invalidContent(String raw) {
        assertInvalidCursor(encodeRaw(raw));
    }

    private static String encodeRaw(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertInvalidCursor(String cursor) {
        assertThatThrownBy(() -> WorkspaceCursor.decode(cursor))
                .isInstanceOf(WorkspaceException.class)
                .satisfies(e -> assertThat(((WorkspaceException) e).getErrorCode())
                        .isEqualTo(WorkspaceErrorCode.INVALID_CURSOR));
    }
}

package com.tree.twig_tree.domain.workspace.dto;

import com.tree.twig_tree.domain.workspace.exception.WorkspaceException;
import com.tree.twig_tree.domain.workspace.exception.code.WorkspaceErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 워크스페이스 목록 커서
 * "마지막으로 받은 워크스페이스"의 정렬 키 (updatedAt, workspaceId) 를 담는다.
 * 클라이언트에는 Base64 문자열로 노출되어 내부 구조를 몰라도 그대로 돌려보내기만 하면 된다.
 */
public record WorkspaceCursor(Instant updatedAt, Long workspaceId) {

    private static final String DELIMITER = "_";

    // URL 쿼리 파라미터에 그대로 넣을 수 있도록 URL-safe + 패딩(=) 제거
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    public String encode() {
        String raw = updatedAt.toString() + DELIMITER + workspaceId;
        return ENCODER.encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    public static WorkspaceCursor decode(String cursor) {
        try {
            String raw = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split(DELIMITER, -1);
            if (parts.length != 2) {
                throw new WorkspaceException(WorkspaceErrorCode.INVALID_CURSOR);
            }
            Instant updatedAt = Instant.parse(parts[0]);
            Long workspaceId = Long.parseLong(parts[1]);
            return new WorkspaceCursor(updatedAt, workspaceId);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            // Base64 디코딩 실패, 숫자 파싱 실패, 날짜 파싱 실패
            throw new WorkspaceException(WorkspaceErrorCode.INVALID_CURSOR);
        }
    }

    // 페이지의 마지막 워크스페이스로부터 다음 커서를 만든다.
    public static WorkspaceCursor from(WorkspaceResDTO.GetWorkspace last){
        return new WorkspaceCursor(last.updatedAt(), last.workspaceId());
    }
}

package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;

/**
 * 문서 하나를 검사하는 동안 압축 해제된 바이트 수를 세는 예산.
 *
 * <p>업로드 크기 상한은 압축된 크기만 제한하므로, 압축률이 극단적인 파일은 라이브러리가 풀면서
 * 힙을 다 써버릴 수 있다. 라이브러리에 넘기기 전에 실제로 풀어보며 바이트를 세고, 상한을 넘는
 * 순간 중단한다. 풀어낸 바이트는 버퍼 하나로 읽고 버리므로 검사 자체는 메모리를 거의 쓰지 않는다.
 *
 * <p>헤더에 적힌 크기는 조작할 수 있으므로 믿지 않고, 읽어낸 바이트만 센다.
 */
@Slf4j
final class InflationBudget {

    /** 문서 전체에서 풀어낼 수 있는 누적 바이트 상한. */
    static final long MAX_TOTAL_BYTES = 100L * 1024L * 1024L;

    /** 엔트리(zip 엔트리, OLE 스트림) 하나를 풀었을 때의 바이트 상한. */
    static final long MAX_ENTRY_BYTES = 50L * 1024L * 1024L;

    /** 엔트리 개수 상한. 작은 엔트리를 대량으로 넣어 메타데이터로 힙을 채우는 경우를 막는다. */
    static final int MAX_ENTRIES = 10_000;

    private static final int BUFFER_SIZE = 8 * 1024;

    private final byte[] buffer = new byte[BUFFER_SIZE];
    private long totalBytes;
    private int entries;

    /**
     * 엔트리 하나를 끝까지 읽으며 바이트를 센다.
     *
     * @throws ChatException 엔트리 수·엔트리 크기·누적 크기 중 하나라도 상한을 넘으면
     *                       {@code FILE_CONTENT_TOO_LARGE}
     * @throws IOException   스트림이 손상되어 읽을 수 없으면. 처리 방침은 호출하는 쪽이 정한다.
     */
    void consume(String name, InputStream in) throws IOException {
        if (++entries > MAX_ENTRIES) {
            throw exceeded("엔트리 수", name, entries);
        }

        long entryBytes = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            entryBytes += read;
            totalBytes += read;
            if (entryBytes > MAX_ENTRY_BYTES) {
                throw exceeded("엔트리 크기", name, entryBytes);
            }
            if (totalBytes > MAX_TOTAL_BYTES) {
                throw exceeded("누적 크기", name, totalBytes);
            }
        }
    }

    private static ChatException exceeded(String limit, String name, long value) {
        log.warn("압축 해제 상한 초과 — {} (엔트리: {}, 값: {})", limit, name, value);
        return new ChatException(ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }
}

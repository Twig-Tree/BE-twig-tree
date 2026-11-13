package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * zip 컨테이너(docx, hwpx)를 라이브러리에 넘기기 전에 실제로 풀어보며 압축 해제 크기를 검사한다.
 *
 * <p>zip 은 같은 파일이라도 로컬 헤더를 순서대로 읽느냐, 끝의 중앙 디렉터리를 읽느냐에 따라
 * 보이는 엔트리가 다를 수 있다. 검사한 엔트리와 라이브러리가 읽는 엔트리가 어긋나면 우회되므로,
 * 각 라이브러리가 읽는 방식과 똑같이 읽는 메서드를 따로 둔다.
 */
@Slf4j
final class ZipBombGuard {

    private ZipBombGuard() {
    }

    /**
     * 로컬 헤더를 순서대로 읽는 방식으로 검사한다.
     * POI 는 InputStream 으로 받은 docx 를 commons-compress {@link ZipArchiveInputStream} 으로 읽는다.
     */
    static void verifyStream(byte[] bytes) {
        InflationBudget budget = new InflationBudget();
        try (ZipArchiveInputStream zip = new ZipArchiveInputStream(new ByteArrayInputStream(bytes))) {
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                budget.consume(entry.getName(), zip);
            }
        } catch (IOException | RuntimeException e) {
            throw parseFailed(e);
        }
    }

    /**
     * 중앙 디렉터리를 읽는 방식으로 검사한다.
     * hwpxlib 은 파일 경로를 받아 {@link ZipFile} 로 읽는다.
     */
    static void verifyFile(Path path) {
        InflationBudget budget = new InflationBudget();
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                try (InputStream in = zip.getInputStream(entry)) {
                    budget.consume(entry.getName(), in);
                }
            }
        } catch (IOException | RuntimeException e) {
            throw parseFailed(e);
        }
    }

    /**
     * zip 이 손상되었거나 zip 이 아니면 라이브러리도 읽지 못하므로, 파싱 실패와 같이 취급한다.
     * 손상된 헤더(잘못된 엔트리 이름 인코딩 등)는 비검사 예외로 나오기도 해서 함께 받는다.
     * 상한 초과로 던진 {@link ChatException} 은 그대로 다시 던진다.
     */
    private static ChatException parseFailed(Exception e) {
        if (e instanceof ChatException chatException) {
            return chatException;
        }
        log.warn("zip 압축 해제 검사 실패: {}", e.toString());
        return new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
    }
}

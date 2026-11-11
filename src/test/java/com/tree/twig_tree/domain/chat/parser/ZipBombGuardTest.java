package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import static com.tree.twig_tree.domain.chat.parser.MaliciousSamples.MB;
import static com.tree.twig_tree.domain.chat.parser.MaliciousSamples.OVER_ENTRY_LIMIT;
import static com.tree.twig_tree.domain.chat.parser.MaliciousSamples.zipOfZeros;
import static com.tree.twig_tree.domain.chat.parser.MaliciousSamples.zipWithEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZipBombGuardTest {

    @TempDir
    Path tempDir;

    /** 로컬 헤더 방식(POI)과 중앙 디렉터리 방식(hwpxlib) 모두 같은 결과를 내야 한다. */
    abstract class Common {

        abstract void verify(byte[] zip);

        @Test
        @DisplayName("상한 안쪽의 정상 zip 은 통과한다")
        void passesNormalZip() {
            assertThatCode(() -> verify(zipOfZeros(MB, 2 * MB))).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("엔트리 하나가 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
        void rejectsHugeEntry() {
            byte[] bomb = zipOfZeros(OVER_ENTRY_LIMIT);

            assertThat(bomb.length).isLessThan((int) MB);
            assertError(() -> verify(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
        }

        @Test
        @DisplayName("엔트리마다는 상한 안쪽이어도 누적이 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
        void rejectsHugeTotal() {
            long each = 40 * MB;
            byte[] bomb = zipOfZeros(each, each, each);

            assertError(() -> verify(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
        }

        @Test
        @DisplayName("엔트리 수가 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
        void rejectsTooManyEntries() {
            byte[] bomb = zipWithEntries(InflationBudget.MAX_ENTRIES + 1);

            assertError(() -> verify(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
        }

        @Test
        @DisplayName("헤더에 적힌 크기를 줄여도 실제로 풀린 바이트로 판정한다")
        void ignoresForgedSize() {
            byte[] bomb = forgeCentralDirectorySizes(zipOfZeros(OVER_ENTRY_LIMIT), 1);

            assertError(() -> verify(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
        }
    }

    @Nested
    @DisplayName("verifyStream — POI(docx) 가 읽는 방식")
    class LocalHeaders extends Common {

        @Override
        void verify(byte[] zip) {
            ZipBombGuard.verifyStream(zip);
        }
    }

    @Nested
    @DisplayName("verifyFile — hwpxlib(hwpx) 이 읽는 방식")
    class CentralDirectory extends Common {

        @Override
        void verify(byte[] zip) {
            ZipBombGuard.verifyFile(write(zip));
        }

        @Test
        @DisplayName("zip 이 아니면 FILE_PARSE_FAILED")
        void rejectsNonZip() {
            assertError(() -> verify("not a zip".getBytes()), ChatErrorCode.FILE_PARSE_FAILED);
        }
    }

    private Path write(byte[] bytes) {
        try {
            Path path = Files.createTempFile(tempDir, "guard-", ".zip");
            Files.write(path, bytes);
            return path;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 중앙 디렉터리의 압축 해제 크기 필드(오프셋 24)를 모두 {@code size} 로 덮어쓴다.
     * {@code ZipOutputStream} 은 로컬 헤더 대신 데이터 디스크립터에 크기를 적으므로, 로컬 헤더 쪽은 원래 0 이다.
     */
    private static byte[] forgeCentralDirectorySizes(byte[] zip, int size) {
        byte[] forged = zip.clone();
        ByteBuffer buffer = ByteBuffer.wrap(forged).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i + 46 <= forged.length; i++) {
            if (buffer.getInt(i) == 0x02014b50) {
                buffer.putInt(i + 24, size);
            }
        }
        return forged;
    }

    private static void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies((Consumer<Throwable>) e ->
                assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

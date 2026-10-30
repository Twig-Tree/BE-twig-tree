package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * hwpxlib 에는 빈 문서를 만들어주는 도구가 없어, 실제 hwpx 본문 추출은 여기서 검증하지 못한다.
 * 실패 경로와 확장자 라우팅만 다루고, 본문 추출은 실제 한글 파일로 수동 확인이 필요하다.
 */
class HwpxDocumentParserTest {

    private final HwpxDocumentParser parser = new HwpxDocumentParser();

    @Test
    @DisplayName("hwpx 가 아닌 바이트는 FILE_PARSE_FAILED")
    void notAHwpx() {
        byte[] garbage = "이건 그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8);

        assertError(() -> parser.parse(garbage), ChatErrorCode.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("zip 이지만 hwpx 구조가 아니면 FILE_PARSE_FAILED")
    void zipButNotHwpx() {
        assertError(() -> parser.parse(zipWithSingleEntry()), ChatErrorCode.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("파싱에 실패해도 임시 파일을 남기지 않는다")
    void cleansUpTempFileOnFailure() {
        long before = countTempUploads();

        assertError(() -> parser.parse("깨진 파일".getBytes(StandardCharsets.UTF_8)),
            ChatErrorCode.FILE_PARSE_FAILED);

        assertThat(countTempUploads()).isEqualTo(before);
    }

    @Test
    @DisplayName("hwpx 확장자를 담당한다")
    void supportsHwpx() {
        assertThat(parser.supportedExtensions()).containsExactly("hwpx");
    }

    private byte[] zipWithSingleEntry() {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write("안녕".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.finish();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 zip 생성 실패", e);
        }
    }

    private long countTempUploads() {
        try (var files = java.nio.file.Files.list(
            java.nio.file.Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.filter(p -> p.getFileName().toString().startsWith("twigtree-upload-")).count();
        } catch (Exception e) {
            throw new IllegalStateException("임시 디렉터리 조회 실패", e);
        }
    }

    private void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

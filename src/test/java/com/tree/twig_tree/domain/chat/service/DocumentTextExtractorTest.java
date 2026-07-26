package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    private MultipartFile file(String filename, String content) {
        return new MockMultipartFile("file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private MultipartFile file(String filename, byte[] content) {
        return new MockMultipartFile("file", filename, "text/plain", content);
    }

    @Test
    @DisplayName("txt 파일에서 본문을 추출한다")
    void extractTxt() {
        assertThat(extractor.extract(file("note.txt", "  트리 자료구조 정리  ")))
            .isEqualTo("트리 자료구조 정리");
    }

    @Test
    @DisplayName("md 파일도 허용한다")
    void extractMd() {
        assertThat(extractor.extract(file("note.md", "# 제목\n본문"))).isEqualTo("# 제목\n본문");
    }

    @Test
    @DisplayName("확장자 대소문자는 구분하지 않는다")
    void extensionIsCaseInsensitive() {
        assertThat(extractor.extract(file("NOTE.TXT", "내용"))).isEqualTo("내용");
    }

    @Test
    @DisplayName("BOM 이 붙어 있어도 첫 글자가 깨지지 않는다")
    void stripsBom() {
        byte[] withBom = ((char) 0xFEFF + "트리").getBytes(StandardCharsets.UTF_8);
        assertThat(extractor.extract(file("note.txt", withBom))).isEqualTo("트리");
    }

    @Test
    @DisplayName("허용하지 않는 확장자는 UNSUPPORTED_FILE_TYPE")
    void unsupportedExtension() {
        assertError(() -> extractor.extract(file("report.pdf", "내용")), ChatErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("확장자가 없으면 UNSUPPORTED_FILE_TYPE")
    void noExtension() {
        assertError(() -> extractor.extract(file("README", "내용")), ChatErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("빈 파일은 FILE_EMPTY")
    void emptyFile() {
        assertError(() -> extractor.extract(file("note.txt", "")), ChatErrorCode.FILE_EMPTY);
    }

    @Test
    @DisplayName("공백뿐인 파일도 FILE_EMPTY")
    void blankFile() {
        assertError(() -> extractor.extract(file("note.txt", "   \n\t  ")), ChatErrorCode.FILE_EMPTY);
    }

    @Test
    @DisplayName("크기 상한을 넘으면 FILE_TOO_LARGE")
    void tooLarge() {
        byte[] oversized = new byte[(int) DocumentTextExtractor.MAX_FILE_BYTES + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');

        assertError(() -> extractor.extract(file("note.txt", oversized)), ChatErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("본문 길이 상한을 넘으면 FILE_TEXT_TOO_LONG")
    void textTooLong() {
        // 크기 상한(1MB)에는 걸리지 않으면서 글자 수 상한만 넘기도록 ASCII 로 채운다
        String longText = "a".repeat(DocumentTextExtractor.MAX_TEXT_LENGTH + 1);

        assertError(() -> extractor.extract(file("note.txt", longText)), ChatErrorCode.FILE_TEXT_TOO_LONG);
    }

    @Test
    @DisplayName("상한과 같은 길이는 통과한다")
    void exactlyMaxLengthPasses() {
        String text = "a".repeat(DocumentTextExtractor.MAX_TEXT_LENGTH);

        assertThat(extractor.extract(file("note.txt", text))).hasSize(DocumentTextExtractor.MAX_TEXT_LENGTH);
    }

    @Test
    @DisplayName("UTF-8 이 아닌 바이트가 섞이면 FILE_READ_FAILED")
    void invalidEncoding() {
        // 확장자만 txt 로 바꾼 바이너리 파일을 가정한 잘못된 UTF-8 시퀀스
        byte[] invalid = {(byte) 0xC3, (byte) 0x28, (byte) 0xA0, (byte) 0xA1};

        assertError(() -> extractor.extract(file("note.txt", invalid)), ChatErrorCode.FILE_READ_FAILED);
    }

    @Test
    @DisplayName("파일이 null 이면 FILE_EMPTY")
    void nullFile() {
        assertError(() -> extractor.extract(null), ChatErrorCode.FILE_EMPTY);
    }

    private void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

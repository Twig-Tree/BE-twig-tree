package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.parser.DocumentParser;
import com.tree.twig_tree.domain.chat.parser.DocxDocumentParser;
import com.tree.twig_tree.domain.chat.parser.HwpDocumentParser;
import com.tree.twig_tree.domain.chat.parser.HwpxDocumentParser;
import com.tree.twig_tree.domain.chat.parser.PdfDocumentParser;
import com.tree.twig_tree.domain.chat.parser.PlainTextParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor(List.of(
        new PlainTextParser(),
        new PdfDocumentParser(),
        new DocxDocumentParser(),
        new HwpDocumentParser(),
        new HwpxDocumentParser()));

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
    @DisplayName("등록된 파서의 확장자를 모두 지원 목록으로 노출한다")
    void exposesSupportedExtensions() {
        assertThat(extractor.supportedExtensions())
            .containsExactlyInAnyOrder("txt", "md", "pdf", "docx", "hwp", "hwpx");
    }

    @Test
    @DisplayName("담당 파서가 없는 확장자는 UNSUPPORTED_FILE_TYPE")
    void unsupportedExtension() {
        assertError(() -> extractor.extract(file("sheet.xlsx", "내용")), ChatErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("구형 doc 은 지원하지 않는다 — docx 만 허용")
    void legacyDocIsUnsupported() {
        assertError(() -> extractor.extract(file("report.doc", "내용")), ChatErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("확장자가 없으면 UNSUPPORTED_FILE_TYPE")
    void noExtension() {
        assertError(() -> extractor.extract(file("README", "내용")), ChatErrorCode.UNSUPPORTED_FILE_TYPE);
    }

    @Test
    @DisplayName("확장자만 pdf 로 바꾼 텍스트 파일은 FILE_PARSE_FAILED")
    void extensionLiesAboutContent() {
        assertError(() -> extractor.extract(file("fake.pdf", "사실은 그냥 텍스트")), ChatErrorCode.FILE_PARSE_FAILED);
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
    @DisplayName("평문 크기 상한(1MB)을 넘으면 FILE_TOO_LARGE")
    void plainTextTooLarge() {
        byte[] oversized = new byte[(int) (1024L * 1024L) + 1];
        java.util.Arrays.fill(oversized, (byte) 'a');

        assertError(() -> extractor.extract(file("note.txt", oversized)), ChatErrorCode.FILE_TOO_LARGE);
    }

    @Test
    @DisplayName("문서 포맷은 평문보다 큰 상한(10MB)을 쓴다")
    void documentFormatsUseLargerLimit() {
        byte[] twoMegabytes = new byte[2 * 1024 * 1024];
        java.util.Arrays.fill(twoMegabytes, (byte) 'a');

        // 평문이면 크기에서 걸리지만, pdf 는 상한을 통과해 파싱 단계까지 간다
        assertError(() -> extractor.extract(file("note.txt", twoMegabytes)), ChatErrorCode.FILE_TOO_LARGE);
        assertError(() -> extractor.extract(file("doc.pdf", twoMegabytes)), ChatErrorCode.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("문서 크기 상한(10MB)을 넘으면 FILE_TOO_LARGE")
    void documentTooLarge() {
        byte[] oversized = new byte[(int) (10L * 1024L * 1024L) + 1];

        assertError(() -> extractor.extract(file("doc.pdf", oversized)), ChatErrorCode.FILE_TOO_LARGE);
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

    @Test
    @DisplayName("같은 확장자를 두 파서가 주장하면 기동 시점에 막는다")
    void rejectsDuplicateExtensionOwners() {
        DocumentParser duplicate = new DocumentParser() {
            @Override
            public Set<String> supportedExtensions() {
                return Set.of("txt");
            }

            @Override
            public long maxBytes() {
                return 1024L;
            }

            @Override
            public String parse(byte[] bytes) {
                return "";
            }
        };

        assertThatThrownBy(() -> new DocumentTextExtractor(List.of(new PlainTextParser(), duplicate)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("txt");
    }

    private void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

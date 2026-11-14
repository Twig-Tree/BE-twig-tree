package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfDocumentParserTest {

    private final PdfDocumentParser parser = new PdfDocumentParser();

    @Test
    @DisplayName("텍스트 레이어가 있는 PDF 에서 본문을 추출한다")
    void extractsText() {
        byte[] pdf = pdf("Tree data structure", false);

        assertThat(parser.parse(pdf)).contains("Tree data structure");
    }

    @Test
    @DisplayName("여러 페이지의 본문을 모두 추출한다")
    void extractsAllPages() {
        byte[] pdf = multiPagePdf("First page", "Second page");

        assertThat(parser.parse(pdf))
            .contains("First page")
            .contains("Second page");
    }

    @Test
    @DisplayName("텍스트가 없는 PDF 는 빈 문자열을 돌려준다 — 스캔본은 여기서 걸러진다")
    void emptyPdfReturnsBlank() {
        byte[] pdf = blankPdf();

        assertThat(parser.parse(pdf).strip()).isEmpty();
    }

    @Test
    @DisplayName("암호가 걸린 PDF 는 FILE_ENCRYPTED")
    void encryptedPdf() {
        byte[] pdf = pdf("secret", true);

        assertError(() -> parser.parse(pdf), ChatErrorCode.FILE_ENCRYPTED);
    }

    @Test
    @DisplayName("PDF 가 아닌 바이트는 FILE_PARSE_FAILED")
    void notAPdf() {
        byte[] garbage = "이건 그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8);

        assertError(() -> parser.parse(garbage), ChatErrorCode.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("pdf 확장자를 담당한다")
    void supportsPdf() {
        assertThat(parser.supportedExtensions()).containsExactly("pdf");
    }

    @Test
    @DisplayName("PDFBox 디코딩 상한이 설치된다 — PDFBox 업그레이드로 깨지면 여기서 잡힌다")
    void decodeLimiterInstalled() {
        parser.parse(pdf("warm up", false));

        assertThat(PdfDecodeLimiter.isInstalled()).isTrue();
    }

    @Test
    @DisplayName("디코딩하면 상한을 넘는 스트림이 있으면 FILE_CONTENT_TOO_LARGE")
    void rejectsInflatedStream() {
        byte[] bomb = pdfWithInflatedContent(MaliciousSamples.OVER_ENTRY_LIMIT);

        assertThat(bomb.length).isLessThan((int) MaliciousSamples.MB);
        assertError(() -> parser.parse(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }

    @Test
    @DisplayName("상한에 걸린 뒤에도 다음 정상 PDF 는 그대로 처리한다")
    void recoversAfterLimit() {
        assertError(() -> parser.parse(pdfWithInflatedContent(MaliciousSamples.OVER_ENTRY_LIMIT)),
            ChatErrorCode.FILE_CONTENT_TOO_LARGE);

        assertThat(parser.parse(pdf("Tree data structure", false))).contains("Tree data structure");
    }

    @Test
    @DisplayName("페이지 수가 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
    void rejectsTooManyPages() {
        byte[] pdf = blankPdf(PdfDocumentParser.MAX_PAGES + 1);

        assertError(() -> parser.parse(pdf), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }

    @Test
    @DisplayName("추출 텍스트가 상한을 넘으면 끝까지 쌓지 않고 FILE_TEXT_TOO_LONG")
    void stopsOnLongText() {
        byte[] pdf = denseTextPdf(PdfDocumentParser.MAX_EXTRACTED_LENGTH + 10_000);

        assertError(() -> parser.parse(pdf), ChatErrorCode.FILE_TEXT_TOO_LONG);
    }

    @Test
    @DisplayName("여러 페이지가 같은 본문 스트림을 참조하면 한 번만 풀고, 참조마다 작업량에 센다")
    void decodesSharedContentOnce() throws Exception {
        byte[] pdf = pdfWithSharedContent(60, MaliciousSamples.MB);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PdfDecodeLimiter.PageContentScan scan = PdfDecodeLimiter.verifyPageContents(document);

            assertThat(scan.decodedBytes()).isEqualTo(MaliciousSamples.MB);
            assertThat(scan.chargedBytes()).isEqualTo(60 * MaliciousSamples.MB);
        }
        assertThat(parser.parse(pdf).strip()).isEmpty();
    }

    @Test
    @DisplayName("같은 본문 스트림을 반복 참조해 문서 전체 작업량이 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
    void rejectsRepeatedlySharedContent() {
        long pages = PdfDecodeLimiter.MAX_PAGE_CONTENT_BYTES / MaliciousSamples.MB + 1;
        byte[] pdf = pdfWithSharedContent((int) pages, MaliciousSamples.MB);

        assertThat(pdf.length).isLessThan((int) MaliciousSamples.MB);
        assertError(() -> parser.parse(pdf), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }

    @Test
    @DisplayName("본문 스트림마다는 상한 안쪽이어도 문서 전체가 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
    void rejectsHugeTotalContent() {
        byte[] pdf = pdfWithDistinctContents(3, 40 * MaliciousSamples.MB);

        assertError(() -> parser.parse(pdf), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }

    @Test
    @DisplayName("본문이 스트림 배열이어도 문서 전체 작업량에 센다")
    void chargesContentArrays() throws Exception {
        byte[] pdf = pdfWithSharedContentArray(30, MaliciousSamples.MB);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PdfDecodeLimiter.PageContentScan scan = PdfDecodeLimiter.verifyPageContents(document);

            assertThat(scan.decodedBytes()).isEqualTo(2 * MaliciousSamples.MB);
            assertThat(scan.chargedBytes()).isEqualTo(60 * MaliciousSamples.MB);
        }
    }

    @Test
    @DisplayName("한 페이지에 글리프가 몰려 있으면 모으는 단계에서 끊고 FILE_TEXT_TOO_LONG")
    void stopsOnGlyphsConcentratedInOnePage() {
        byte[] pdf = singlePageDenseTextPdf(PdfDocumentParser.MAX_GLYPH_CHARS + 1_000);

        assertError(() -> parser.parse(pdf), ChatErrorCode.FILE_TEXT_TOO_LONG);
    }

    @Test
    @DisplayName("글리프 상한은 페이지를 출력(writePage)하기 전, 글리프를 모으는 중에 걸린다")
    void glyphLimitTriggersBeforeWritePage() throws Exception {
        byte[] pdf = singlePageDenseTextPdf(PdfDocumentParser.MAX_GLYPH_CHARS + 1_000);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            PdfDocumentParser.LimitedTextStripper stripper =
                new PdfDocumentParser.LimitedTextStripper(PdfDocumentParser.MAX_GLYPH_CHARS);
            StringWriter writer = new StringWriter();

            assertThatThrownBy(() -> stripper.writeText(document, writer))
                .isInstanceOf(PdfDocumentParser.LimitedTextStripper.LimitExceededException.class);
            assertThat(stripper.limitExceeded()).isTrue();
            assertThat(writer.toString()).isEmpty();
        }
    }

    /** 한 페이지에 {@code glyphs} 개 이상의 글자를 1pt 글씨로 채운 PDF. */
    private byte[] singlePageDenseTextPdf(int glyphs) {
        String line = "a".repeat(1_000);
        int lines = glyphs / line.length() + 1;
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 1);
                content.setLeading(1);
                content.newLineAtOffset(20, 750);
                for (int l = 0; l < lines; l++) {
                    content.showText(line);
                    content.newLine();
                }
                content.endText();
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    /** {@code pages} 개 페이지가 공백 {@code size} 바이트짜리 본문 스트림 하나를 함께 참조하는 PDF. */
    private byte[] pdfWithSharedContent(int pages, long size) {
        try (PDDocument document = new PDDocument()) {
            PDStream shared = new PDStream(document, spaces(size), COSName.FLATE_DECODE);
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                page.setContents(shared);
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    /** 페이지마다 공백 {@code size} 바이트짜리 본문 스트림을 따로 가진 PDF. */
    private byte[] pdfWithDistinctContents(int pages, long size) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                page.setContents(new PDStream(document, spaces(size), COSName.FLATE_DECODE));
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    /** {@code pages} 개 페이지가 같은 본문 스트림 두 개를 배열로 함께 참조하는 PDF. */
    private byte[] pdfWithSharedContentArray(int pages, long size) {
        try (PDDocument document = new PDDocument()) {
            List<PDStream> shared = List.of(
                new PDStream(document, spaces(size), COSName.FLATE_DECODE),
                new PDStream(document, spaces(size), COSName.FLATE_DECODE));
            for (int i = 0; i < pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                page.setContents(shared);
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    /** 본문 스트림이 공백 {@code size} 바이트로 풀리는 PDF. 공백은 본문 문법상 유효하다. */
    private byte[] pdfWithInflatedContent(long size) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            page.setContents(new PDStream(document, spaces(size), COSName.FLATE_DECODE));
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    private byte[] blankPdf(int pages) {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) {
                document.addPage(new PDPage());
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    /** 최소 {@code length} 글자가 추출되도록 작은 글씨로 페이지를 채운 PDF. */
    private byte[] denseTextPdf(int length) {
        String line = "a".repeat(100);
        int linesPerPage = 50;
        int pages = length / (line.length() * linesPerPage) + 1;
        try (PDDocument document = new PDDocument()) {
            for (int p = 0; p < pages; p++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 4);
                    content.setLeading(6);
                    content.newLineAtOffset(20, 750);
                    for (int l = 0; l < linesPerPage; l++) {
                        content.showText(line);
                        content.newLine();
                    }
                    content.endText();
                }
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    private InputStream spaces(long size) {
        return new InputStream() {
            private long remaining = size;

            @Override
            public int read() {
                return remaining-- > 0 ? ' ' : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (remaining <= 0) {
                    return -1;
                }
                int n = (int) Math.min(len, remaining);
                Arrays.fill(b, off, off + n, (byte) ' ');
                remaining -= n;
                return n;
            }
        };
    }

    private byte[] pdf(String text, boolean encrypted) {
        try (PDDocument document = new PDDocument()) {
            addPage(document, text);
            if (encrypted) {
                StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    private byte[] multiPagePdf(String... pageTexts) {
        try (PDDocument document = new PDDocument()) {
            for (String text : pageTexts) {
                addPage(document, text);
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    private byte[] blankPdf() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 PDF 생성 실패", e);
        }
    }

    /** 표준 14 폰트는 한글을 담지 못하므로 본문 검증에는 ASCII 를 쓴다. */
    private void addPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 700);
            content.showText(text);
            content.endText();
        }
    }

    private byte[] toBytes(PDDocument document) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    private void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

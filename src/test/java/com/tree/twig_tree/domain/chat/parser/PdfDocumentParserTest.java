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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

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

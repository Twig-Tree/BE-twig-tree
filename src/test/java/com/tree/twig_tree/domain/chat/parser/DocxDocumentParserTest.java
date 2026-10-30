package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocxDocumentParserTest {

    private final DocxDocumentParser parser = new DocxDocumentParser();

    @Test
    @DisplayName("docx 본문을 추출한다 — 한글 포함")
    void extractsKoreanText() {
        byte[] docx = docx("트리 자료구조 정리");

        assertThat(parser.parse(docx)).contains("트리 자료구조 정리");
    }

    @Test
    @DisplayName("문단이 여러 개면 모두 추출한다")
    void extractsMultipleParagraphs() {
        byte[] docx = docx("첫 번째 문단", "두 번째 문단");

        assertThat(parser.parse(docx))
            .contains("첫 번째 문단")
            .contains("두 번째 문단");
    }

    @Test
    @DisplayName("표 안의 글자도 추출한다")
    void extractsTableText() {
        byte[] docx = docxWithTable("표 안의 내용");

        assertThat(parser.parse(docx)).contains("표 안의 내용");
    }

    @Test
    @DisplayName("docx 가 아닌 바이트는 FILE_PARSE_FAILED")
    void notADocx() {
        byte[] garbage = "이건 그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8);

        assertError(() -> parser.parse(garbage), ChatErrorCode.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("docx 확장자를 담당한다")
    void supportsDocx() {
        assertThat(parser.supportedExtensions()).containsExactly("docx");
    }

    private byte[] docx(String... paragraphs) {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String text : paragraphs) {
                document.createParagraph().createRun().setText(text);
            }
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 DOCX 생성 실패", e);
        }
    }

    private byte[] docxWithTable(String cellText) {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFTable table = document.createTable(1, 1);
            table.getRow(0).getCell(0).setText(cellText);
            return toBytes(document);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 DOCX 생성 실패", e);
        }
    }

    private byte[] toBytes(XWPFDocument document) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.write(out);
        return out.toByteArray();
    }

    private void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

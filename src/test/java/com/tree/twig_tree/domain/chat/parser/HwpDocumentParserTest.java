package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.object.bodytext.Section;
import kr.dogfoot.hwplib.object.bodytext.paragraph.Paragraph;
import kr.dogfoot.hwplib.tool.blankfilemaker.BlankFileMaker;
import kr.dogfoot.hwplib.writer.HWPWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HwpDocumentParserTest {

    private final HwpDocumentParser parser = new HwpDocumentParser();

    @Test
    @DisplayName("hwp 본문을 추출한다 — 한글 포함")
    void extractsKoreanText() {
        byte[] hwp = hwp("트리 자료구조 정리");

        assertThat(parser.parse(hwp)).contains("트리 자료구조 정리");
    }

    @Test
    @DisplayName("암호가 설정된 hwp 는 FILE_ENCRYPTED")
    void passwordProtected() {
        HWPFile file = BlankFileMaker.make();
        file.getFileHeader().setHasPassword(true);

        assertError(() -> parser.extractFrom(file), ChatErrorCode.FILE_ENCRYPTED);
    }

    @Test
    @DisplayName("DRM 문서도 FILE_ENCRYPTED")
    void drmProtected() {
        HWPFile file = BlankFileMaker.make();
        file.getFileHeader().setDRMDocument(true);

        assertError(() -> parser.extractFrom(file), ChatErrorCode.FILE_ENCRYPTED);
    }

    @Test
    @DisplayName("hwp 가 아닌 바이트는 FILE_PARSE_FAILED")
    void notAHwp() {
        byte[] garbage = "이건 그냥 텍스트입니다".getBytes(StandardCharsets.UTF_8);

        assertError(() -> parser.parse(garbage), ChatErrorCode.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("hwp 확장자를 담당한다")
    void supportsHwp() {
        assertThat(parser.supportedExtensions()).containsExactly("hwp");
    }

    private byte[] hwp(String text) {
        HWPFile file = BlankFileMaker.make();
        writeText(file, text);
        return toBytes(file);
    }

    private void writeText(HWPFile file, String text) {
        try {
            Section section = file.getBodyText().getSectionList().get(0);
            Paragraph paragraph =
                section.getParagraphCount() > 0 ? section.getParagraph(0) : section.addNewParagraph();
            if (paragraph.getText() == null) {
                paragraph.createText();
            }
            paragraph.getText().addString(text);
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 HWP 본문 작성 실패", e);
        }
    }

    private byte[] toBytes(HWPFile file) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            HWPWriter.toStream(file, out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("테스트용 HWP 생성 실패", e);
        }
    }

    private void assertError(Runnable action, ChatErrorCode expected) {
        assertThatThrownBy(action::run)
            .isInstanceOf(ChatException.class)
            .satisfies(e -> assertThat(((ChatException) e).getErrorCode()).isEqualTo(expected));
    }
}

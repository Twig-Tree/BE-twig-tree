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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("압축을 풀면 상한을 넘는 스트림이 있으면 hwplib 에 넘기기 전에 FILE_CONTENT_TOO_LARGE")
    void rejectsInflatedStream() {
        byte[] bomb = MaliciousSamples.hwpWithInflatedSection(MaliciousSamples.OVER_ENTRY_LIMIT);

        assertError(() -> parser.parse(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DefaultJScript", "JScriptVersion"})
    @DisplayName("배포용 문서의 암호화된 스크립트도 복호화한 뒤 풀어보고, 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
    void rejectsInflatedDistributionScript(String scriptName) {
        byte[] bomb = MaliciousSamples.hwpWithInflatedScript(scriptName, MaliciousSamples.OVER_ENTRY_LIMIT, true);

        assertError(() -> parser.parse(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"DefaultJScript", "JScriptVersion"})
    @DisplayName("일반 문서의 스크립트는 복호화 없이 풀어보고, 상한을 넘으면 FILE_CONTENT_TOO_LARGE")
    void rejectsInflatedScript(String scriptName) {
        byte[] bomb = MaliciousSamples.hwpWithInflatedScript(scriptName, MaliciousSamples.OVER_ENTRY_LIMIT, false);

        assertError(() -> parser.parse(bomb), ChatErrorCode.FILE_CONTENT_TOO_LARGE);
    }

    @Test
    @DisplayName("블록 체인이 손상되어 스트림을 열 때 비검사 예외가 나면 FILE_PARSE_FAILED")
    void brokenBlockChain() {
        byte[] broken = MaliciousSamples.hwpWithEmptyMiniStreamChain();

        assertError(() -> parser.parse(broken), ChatErrorCode.FILE_PARSE_FAILED);
    }

    @Test
    @DisplayName("배포용 문서 복호화 메서드를 찾는다 — hwplib 업그레이드로 깨지면 여기서 잡힌다")
    void canVerifyDistributionDocuments() {
        assertThat(HwpOleGuard.canVerifyDistributionDocuments()).isTrue();
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

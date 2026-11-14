package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Set;

/**
 * PDF 파서.
 *
 * <p>텍스트 레이어가 있는 PDF 만 처리한다. 스캔본(이미지만 있는 PDF)은 추출 결과가 비어
 * {@code FILE_EMPTY} 로 응답된다 — OCR 은 지원하지 않는다.
 *
 * <p>PDF 는 미리 풀어볼 수 없어 네 군데서 막는다. 스트림 디코딩 크기와 페이지 본문 작업량은
 * {@link PdfDecodeLimiter} 가, 페이지 수는 {@link #MAX_PAGES} 가, 모아두는 글리프 수는
 * {@link LimitedTextStripper} 가, 출력 텍스트 길이는 {@link LimitedWriter} 가 제한한다.
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    /** 페이지 수 상한. 빈 페이지를 대량으로 넣어 추출 시간을 끄는 경우를 막는다. */
    static final int MAX_PAGES = 1_000;

    /**
     * 추출 중 쌓을 수 있는 텍스트 길이 상한. 정확한 길이 판정은 앞뒤 공백을 걷어낸 뒤
     * {@code DocumentTextExtractor} 가 하므로, 여기서는 여유를 두고 끊기만 한다.
     */
    static final int MAX_EXTRACTED_LENGTH = MAX_TEXT_LENGTH * 2;

    /**
     * 문서 전체에서 모아둘 수 있는 글리프 문자 수 상한. PDFTextStripper 는 페이지의 글리프를 전부 모은 뒤
     * 출력하므로 Writer 검사보다 먼저 막아야 한다. 겹쳐 찍은 글자(가짜 볼드 등)는 출력 전에 걸러지므로
     * 출력 상한보다 여유를 둔다.
     */
    static final int MAX_GLYPH_CHARS = MAX_EXTRACTED_LENGTH * 2;

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("pdf");
    }

    @Override
    public String parse(byte[] bytes) {
        if (!PdfDecodeLimiter.isInstalled()) {
            log.error("PDF 디코딩 상한이 설치되지 않아 PDF 를 처리하지 않습니다.");
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }

        PdfDecodeLimiter.reset();
        try {
            String text = extract(bytes);
            if (PdfDecodeLimiter.exceeded()) {
                throw new ChatException(ChatErrorCode.FILE_CONTENT_TOO_LARGE);
            }
            return text;
        } finally {
            PdfDecodeLimiter.reset();
        }
    }

    private String extract(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > MAX_PAGES) {
                log.warn("PDF 페이지 수 상한 초과: {}", document.getNumberOfPages());
                throw new ChatException(ChatErrorCode.FILE_CONTENT_TOO_LARGE);
            }
            PdfDecodeLimiter.verifyPageContents(document);

            LimitedTextStripper stripper = new LimitedTextStripper(MAX_GLYPH_CHARS);
            stripper.setSortByPosition(true);
            LimitedWriter writer = new LimitedWriter(MAX_EXTRACTED_LENGTH);
            stripper.writeText(document, writer);
            if (stripper.limitExceeded()) {
                // PDFBox 가 중간에 예외를 삼키고 진행했더라도 상한을 넘었으면 받지 않는다.
                throw new ChatException(ChatErrorCode.FILE_TEXT_TOO_LONG);
            }
            return writer.toString();
        } catch (InvalidPasswordException e) {
            log.warn("암호가 걸린 PDF: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_ENCRYPTED);
        } catch (IOException | RuntimeException e) {
            if (e instanceof ChatException chatException) {
                throw chatException;
            }
            if (PdfDecodeLimiter.exceeded()) {
                throw new ChatException(ChatErrorCode.FILE_CONTENT_TOO_LARGE);
            }
            if (e instanceof LimitedWriter.LimitExceededException
                || e instanceof LimitedTextStripper.LimitExceededException) {
                throw new ChatException(ChatErrorCode.FILE_TEXT_TOO_LONG);
            }
            log.warn("PDF 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }
    }

    /**
     * 글리프를 모으는 단계에서 문자 수를 세다가 상한을 넘으면 끊는 PDFTextStripper.
     * 한 페이지에 글리프가 몰려 있으면 출력(writePage) 전에 TextPosition 만으로 힙이 찰 수 있다.
     */
    static final class LimitedTextStripper extends PDFTextStripper {

        private final int maxChars;
        private long chars;
        private boolean exceeded;

        LimitedTextStripper(int maxChars) {
            this.maxChars = maxChars;
        }

        @Override
        protected void processTextPosition(TextPosition text) {
            String unicode = text.getUnicode();
            chars += unicode == null || unicode.isEmpty() ? 1 : unicode.length();
            if (chars > maxChars) {
                exceeded = true;
                throw new LimitExceededException();
            }
            super.processTextPosition(text);
        }

        boolean limitExceeded() {
            return exceeded;
        }

        static final class LimitExceededException extends RuntimeException {
            LimitExceededException() {
                super("추출 글리프 수가 상한을 초과했습니다.");
            }
        }
    }

    /** 상한을 넘는 텍스트가 들어오면 더 쌓지 않고 끊는 Writer. */
    static final class LimitedWriter extends StringWriter {

        private final int maxLength;

        LimitedWriter(int maxLength) {
            this.maxLength = maxLength;
        }

        @Override
        public void write(int c) {
            reserve(1);
            super.write(c);
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            reserve(len);
            super.write(cbuf, off, len);
        }

        @Override
        public void write(String str, int off, int len) {
            reserve(len);
            super.write(str, off, len);
        }

        @Override
        public void write(String str) {
            reserve(str.length());
            super.write(str);
        }

        private void reserve(int len) {
            if (getBuffer().length() + len > maxLength) {
                throw new LimitExceededException();
            }
        }

        static final class LimitExceededException extends RuntimeException {
            LimitExceededException() {
                super("추출 텍스트가 상한을 초과했습니다.");
            }
        }
    }
}

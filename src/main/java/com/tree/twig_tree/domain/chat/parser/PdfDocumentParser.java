package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * <p>PDF 는 미리 풀어볼 수 없어 세 군데서 막는다. 스트림 디코딩 한 번의 크기는
 * {@link PdfDecodeLimiter} 가, 페이지 수는 {@link #MAX_PAGES} 가, 추출 텍스트 길이는
 * {@link LimitedWriter} 가 제한한다.
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

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            LimitedWriter writer = new LimitedWriter(MAX_EXTRACTED_LENGTH);
            stripper.writeText(document, writer);
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
            if (e instanceof LimitedWriter.LimitExceededException) {
                throw new ChatException(ChatErrorCode.FILE_TEXT_TOO_LONG);
            }
            log.warn("PDF 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
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

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
import java.util.Set;

/**
 * PDF 파서.
 *
 * <p>텍스트 레이어가 있는 PDF 만 처리한다. 스캔본(이미지만 있는 PDF)은 추출 결과가 비어
 * {@code FILE_EMPTY} 로 응답된다 — OCR 은 지원하지 않는다.
 */
@Slf4j
@Component
public class PdfDocumentParser implements DocumentParser {

    static final long MAX_BYTES = 10L * 1024L * 1024L;

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("pdf");
    }

    @Override
    public long maxBytes() {
        return MAX_BYTES;
    }

    @Override
    public String parse(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        } catch (InvalidPasswordException e) {
            log.warn("암호가 걸린 PDF: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_ENCRYPTED);
        } catch (IOException | RuntimeException e) {
            log.warn("PDF 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }
    }
}

package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.EncryptedDocumentException;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * DOCX(워드) 파서.
 *
 * <p>구형 바이너리 {@code .doc} 는 별도 포맷이라 지원하지 않는다. 확장자만 docx 로 바꾼
 * doc 파일은 파싱 단계에서 걸러진다.
 */
@Slf4j
@Component
public class DocxDocumentParser implements DocumentParser {

    static final long MAX_BYTES = 10L * 1024L * 1024L;

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("docx");
    }

    @Override
    public long maxBytes() {
        return MAX_BYTES;
    }

    @Override
    public String parse(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (EncryptedDocumentException e) {
            log.warn("암호가 걸린 DOCX: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_ENCRYPTED);
        } catch (IOException | RuntimeException e) {
            log.warn("DOCX 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }
    }
}

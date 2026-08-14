package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import kr.dogfoot.hwpxlib.object.HWPXFile;
import kr.dogfoot.hwpxlib.reader.HWPXReader;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwpxlib.tool.textextractor.TextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * HWPX(한글 XML 포맷) 파서.
 *
 * <p>hwpxlib 의 리더는 파일 경로만 받고 InputStream 을 지원하지 않아, 다른 파서와 달리
 * 임시 파일을 거쳐야 한다. 임시 파일은 추출 직후 지우고, 실패해도 JVM 종료 시 정리되도록 표시한다.
 */
@Slf4j
@Component
public class HwpxDocumentParser implements DocumentParser {

    static final long MAX_BYTES = 10L * 1024L * 1024L;

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("hwpx");
    }

    @Override
    public long maxBytes() {
        return MAX_BYTES;
    }

    @Override
    public String parse(byte[] bytes) {
        Path temp = writeTempFile(bytes);
        try {
            HWPXFile hwpxFile = HWPXReader.fromFile(temp.toFile());
            return TextExtractor.extract(
                hwpxFile, TextExtractMethod.InsertControlTextBetweenParagraphText, false, null);
        } catch (Exception e) {
            log.warn("HWPX 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        } finally {
            deleteQuietly(temp);
        }
    }

    private Path writeTempFile(byte[] bytes) {
        try {
            Path temp = Files.createTempFile("twigtree-upload-", ".hwpx");
            temp.toFile().deleteOnExit();
            Files.write(temp, bytes);
            return temp;
        } catch (IOException e) {
            log.warn("HWPX 임시 파일 생성 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_READ_FAILED);
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("HWPX 임시 파일 삭제 실패: {}", path);
        }
    }
}

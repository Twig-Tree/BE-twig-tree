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
 *
 * <p>hwpx 는 zip 컨테이너라 hwpxlib 에 넘기기 전에 {@link ZipBombGuard} 로 압축 해제 크기를 먼저 검사한다.
 */
@Slf4j
@Component
public class HwpxDocumentParser implements DocumentParser {

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("hwpx");
    }

    @Override
    public String parse(byte[] bytes) {
        Path temp = writeTempFile(bytes);
        try {
            ZipBombGuard.verifyFile(temp);
            return extract(temp);
        } finally {
            deleteQuietly(temp);
        }
    }

    /** hwpxlib 은 checked Exception 을 그대로 던지므로 여기서 도메인 예외로 바꾼다. */
    private String extract(Path temp) {
        try {
            HWPXFile hwpxFile = HWPXReader.fromFile(temp.toFile());
            return TextExtractor.extract(
                hwpxFile, TextExtractMethod.InsertControlTextBetweenParagraphText, false, null);
        } catch (Exception e) {
            log.warn("HWPX 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
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

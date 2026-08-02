package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import kr.dogfoot.hwplib.object.HWPFile;
import kr.dogfoot.hwplib.reader.HWPReader;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractMethod;
import kr.dogfoot.hwplib.tool.textextractor.TextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Set;

/**
 * HWP(한글 바이너리 포맷) 파서.
 *
 * <p>표준 라이브러리가 없어 서드파티 hwplib 에 의존한다. 암호 문서와 DRM 문서는 본문을 읽을 수
 * 없으므로 파싱 전에 헤더에서 걸러낸다.
 */
@Slf4j
@Component
public class HwpDocumentParser implements DocumentParser {

    static final long MAX_BYTES = 10L * 1024L * 1024L;

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("hwp");
    }

    @Override
    public long maxBytes() {
        return MAX_BYTES;
    }

    @Override
    public String parse(byte[] bytes) {
        return extractFrom(read(bytes));
    }

    /**
     * 읽어들인 HWP 객체에서 본문을 뽑는다.
     *
     * <p>암호·DRM 문서는 hwplib 이 본문을 온전히 읽지 못하므로 추출 전에 헤더에서 걸러낸다.
     * 암호가 걸린 hwp 파일은 코드로 만들어낼 수 없어, 이 지점을 테스트에서 직접 호출한다.
     */
    String extractFrom(HWPFile hwpFile) {
        if (hwpFile.getFileHeader().hasPassword() || hwpFile.getFileHeader().isDRMDocument()) {
            throw new ChatException(ChatErrorCode.FILE_ENCRYPTED);
        }

        try {
            return TextExtractor.extract(hwpFile, TextExtractMethod.InsertControlTextBetweenParagraphText);
        } catch (Exception e) {
            log.warn("HWP 본문 추출 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }
    }

    /** hwplib 의 리더는 checked Exception 을 그대로 던지므로 여기서 도메인 예외로 바꾼다. */
    private HWPFile read(byte[] bytes) {
        try {
            return HWPReader.fromInputStream(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            log.warn("HWP 파싱 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }
    }
}

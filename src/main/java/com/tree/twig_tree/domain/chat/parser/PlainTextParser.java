package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * txt / md 같은 평문 파일 파서.
 */
@Slf4j
@Component
public class PlainTextParser implements DocumentParser {

    /** 평문은 내용 그대로가 파일 크기이므로 본문 길이 상한과 비슷한 수준으로 잡는다. */
    static final long MAX_BYTES = 1024L * 1024L;

    /** UTF-8 파일 앞에 붙을 수 있는 BOM(U+FEFF). 그대로 두면 첫 글자가 깨져 보인다. */
    private static final char BOM = 0xFEFF;

    @Override
    public Set<String> supportedExtensions() {
        return Set.of("txt", "md");
    }

    @Override
    public long maxBytes() {
        return MAX_BYTES;
    }

    @Override
    public String parse(byte[] bytes) {
        return stripBom(decodeUtf8(bytes));
    }

    /**
     * 잘못된 바이트를 대체 문자로 흘려보내지 않고 예외로 처리한다.
     * 확장자만 txt 로 바꾼 바이너리 파일이 깨진 텍스트로 LLM 에 전달되는 것을 막는다.
     */
    private String decodeUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            log.warn("UTF-8 디코딩 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_READ_FAILED);
        }
    }

    private String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == BOM ? text.substring(1) : text;
    }
}

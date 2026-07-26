package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 업로드된 텍스트 파일에서 LLM 입력으로 쓸 본문을 추출한다.
 *
 * <p>원본 파일은 저장하지 않고 요청을 처리하는 동안 메모리에서만 다룬다.
 * 파일 내용은 신뢰할 수 없는 외부 입력이므로 확장자·크기·인코딩·길이를 모두 검증한다.
 */
@Slf4j
@Component
public class DocumentTextExtractor {

    /** 허용 확장자. 그 외 포맷은 별도 파서가 필요하므로 현재는 막는다. */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("txt", "md");

    /** 파일 크기 상한. spring.servlet.multipart.max-file-size 보다 작게 잡아 이쪽에서 먼저 걸리게 한다. */
    static final long MAX_FILE_BYTES = 1024L * 1024L;

    /** 본문 길이 상한. LLM 컨텍스트 한계와 호출 비용을 고려한 값. */
    static final int MAX_TEXT_LENGTH = 20_000;

    /** UTF-8 파일 앞에 붙을 수 있는 BOM(U+FEFF). 그대로 두면 첫 글자가 깨져 보인다. */
    private static final char BOM = 0xFEFF;

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ChatException(ChatErrorCode.FILE_EMPTY);
        }

        validateExtension(file.getOriginalFilename());

        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ChatException(ChatErrorCode.FILE_TOO_LARGE);
        }

        String text = stripBom(decodeUtf8(readBytes(file))).strip();

        if (text.isEmpty()) {
            throw new ChatException(ChatErrorCode.FILE_EMPTY);
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new ChatException(ChatErrorCode.FILE_TEXT_TOO_LONG);
        }

        return text;
    }

    /**
     * 파일명은 확장자 판별에만 쓴다. 파일을 디스크에 쓰지 않으므로 경로 탈출은 문제가 되지 않는다.
     */
    private void validateExtension(String filename) {
        if (filename == null) {
            throw new ChatException(ChatErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new ChatException(ChatErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ChatException(ChatErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("업로드 파일 읽기 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_READ_FAILED);
        }
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

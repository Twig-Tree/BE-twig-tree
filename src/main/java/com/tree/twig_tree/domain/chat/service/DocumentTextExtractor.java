package com.tree.twig_tree.domain.chat.service;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import com.tree.twig_tree.domain.chat.parser.DocumentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 업로드된 문서에서 LLM 입력으로 쓸 본문을 추출한다.
 *
 * <p>포맷별 추출은 {@link DocumentParser} 구현체에 위임하고, 이 클래스는 공통 검증
 * (확장자 판별 · 크기 · 본문 길이)과 파서 선택만 책임진다.
 *
 * <p>원본 파일은 저장하지 않고 요청을 처리하는 동안만 다룬다.
 * 파일 내용은 신뢰할 수 없는 외부 입력이므로 확장자·크기·길이를 모두 검증한다.
 */
@Slf4j
@Component
public class DocumentTextExtractor {

    /** 본문 길이 상한. LLM 컨텍스트 한계와 호출 비용을 고려한 값. */
    static final int MAX_TEXT_LENGTH = 20_000;

    /** 확장자 → 담당 파서. 스프링이 주입한 구현체로부터 한 번만 만든다. */
    private final Map<String, DocumentParser> parsersByExtension;

    public DocumentTextExtractor(List<DocumentParser> parsers) {
        Map<String, DocumentParser> index = new HashMap<>();
        for (DocumentParser parser : parsers) {
            for (String extension : parser.supportedExtensions()) {
                DocumentParser previous = index.put(extension, parser);
                if (previous != null) {
                    throw new IllegalStateException(
                        "확장자 %s 를 처리하는 파서가 둘 이상입니다: %s, %s"
                            .formatted(extension, previous.getClass().getSimpleName(),
                                parser.getClass().getSimpleName()));
                }
            }
        }
        this.parsersByExtension = Map.copyOf(index);
    }

    /** 지원 확장자 목록. 문서화·안내 문구용. */
    public Set<String> supportedExtensions() {
        return new TreeSet<>(parsersByExtension.keySet());
    }

    public String extract(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ChatException(ChatErrorCode.FILE_EMPTY);
        }

        DocumentParser parser = resolveParser(file.getOriginalFilename());

        if (file.getSize() > parser.maxBytes()) {
            throw new ChatException(ChatErrorCode.FILE_TOO_LARGE);
        }

        String text = parser.parse(readBytes(file)).strip();

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
     *
     * <p>확장자는 실제 형식을 보장하지 않으므로, 내용이 확장자와 다르면 각 파서가 파싱 단계에서
     * {@code FILE_PARSE_FAILED} 로 걸러낸다.
     */
    private DocumentParser resolveParser(String filename) {
        if (filename == null) {
            throw new ChatException(ChatErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            throw new ChatException(ChatErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        DocumentParser parser = parsersByExtension.get(extension);
        if (parser == null) {
            throw new ChatException(ChatErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        return parser;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("업로드 파일 읽기 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_READ_FAILED);
        }
    }
}

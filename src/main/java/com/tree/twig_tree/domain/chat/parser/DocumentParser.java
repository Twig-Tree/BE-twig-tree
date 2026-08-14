package com.tree.twig_tree.domain.chat.parser;

import java.util.Set;

/**
 * 업로드된 문서 한 종류에서 본문 텍스트를 뽑아내는 파서.
 *
 * <p>포맷을 추가할 때는 이 인터페이스 구현체를 빈으로 등록하기만 하면 된다.
 * {@link com.tree.twig_tree.domain.chat.service.DocumentTextExtractor} 가 주입받은 구현체 중에서
 * 확장자로 맞는 것을 골라 쓴다.
 */
public interface DocumentParser {

    /** 이 파서가 처리하는 확장자. 소문자로 반환한다. */
    Set<String> supportedExtensions();

    /**
     * 이 포맷의 파일 크기 상한.
     *
     * <p>텍스트 파일과 달리 PDF·DOCX 같은 바이너리 포맷은 같은 분량이어도 파일이 훨씬 크므로
     * 포맷마다 다른 값을 쓴다.
     */
    long maxBytes();

    /**
     * 파일 바이트에서 본문을 추출한다.
     *
     * <p>빈 문자열·길이 초과 판정은 호출하는 쪽에서 하므로 여기서는 추출만 책임진다.
     * 파싱에 실패하면 {@link com.tree.twig_tree.domain.chat.exception.ChatException} 을 던진다.
     */
    String parse(byte[] bytes);
}

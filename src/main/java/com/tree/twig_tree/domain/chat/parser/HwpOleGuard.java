package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DirectoryEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentInputStream;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.Entry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.POIFSFileSystem;
import kr.dogfoot.hwplib.util.compoundFile.reader.StreamReader;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * HWP(OLE 복합문서)를 hwplib 에 넘기기 전에 스트림 압축 해제 크기를 검사한다.
 *
 * <p>hwplib 은 DocInfo · BodyText · BinData · Scripts 스트림을 raw deflate 로 통째로 풀어
 * {@code byte[]} 에 담는다. 압축 여부는 파일 헤더 플래그와 BinData 항목별 설정에 따라 달라지므로,
 * 여기서는 플래그를 따지지 않고 모든 스트림을 raw deflate 로 풀어본다. 압축되지 않은 스트림은
 * 금방 형식 오류가 나서 원래 크기 이하만 세고 끝난다.
 *
 * <p>배포용 문서는 본문이 {@code ViewText} 아래에 AES 로 암호화된 뒤 압축되어 있다. 복호화는
 * hwplib 내부 메서드를 그대로 써야 똑같이 풀리므로 리플렉션으로 호출한다. 라이브러리 업그레이드로
 * 메서드를 찾지 못하면 배포용 문서는 검사할 수 없으니 받지 않는다.
 */
@Slf4j
final class HwpOleGuard {

    /** 배포용 문서의 본문 스토리지. 이 아래 스트림은 암호화되어 있다. */
    private static final String DISTRIBUTION_STORAGE = "ViewText";

    /** hwplib 의 {@code StreamReader.documentInputStream(DocumentEntry, boolean)} — 복호화된(압축 상태) 스트림. */
    private static final Method DISTRIBUTION_STREAM = resolveDistributionStream();

    private HwpOleGuard() {
    }

    static void verify(byte[] bytes) {
        POIFSFileSystem fs;
        try {
            fs = new POIFSFileSystem(new ByteArrayInputStream(bytes));
        } catch (IOException | RuntimeException e) {
            log.warn("HWP 복합문서 열기 실패: {}", e.getMessage());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }

        // hwplib 에 포함된 구버전 POIFS 는 Closeable 이 아니다. 바이트 배열 기반이라 닫을 자원도 없다.
        try {
            walk(fs.getRoot(), new InflationBudget(), false);
        } catch (ChatException e) {
            throw e;
        } catch (RuntimeException e) {
            // 블록 체인이 손상된 문서는 스트림을 여는 시점에 POIFS 가 비검사 예외를 던진다.
            // (예: 선언된 크기는 양수인데 소형 스트림 체인이 비어 있으면 배열 범위 예외)
            log.warn("HWP 스트림 순회 실패: {}", e.toString());
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }
    }

    /** 배포용 문서를 검사할 수 있는지. 라이브러리 업그레이드 후 회귀를 테스트에서 잡기 위해 둔다. */
    static boolean canVerifyDistributionDocuments() {
        return DISTRIBUTION_STREAM != null;
    }

    private static void walk(DirectoryEntry directory, InflationBudget budget, boolean encrypted) {
        for (Entry entry : directory) {
            if (entry instanceof DirectoryEntry child) {
                walk(child, budget, encrypted || DISTRIBUTION_STORAGE.equals(child.getName()));
            } else if (entry instanceof DocumentEntry document) {
                inflate(document, budget, encrypted);
            }
        }
    }

    /**
     * 스트림을 raw deflate 로 풀며 바이트를 센다. 상한 초과는 그대로 던지고, 형식 오류는 무시한다.
     * 압축되지 않은 스트림이거나 손상된 스트림이면 hwplib 이 직접 판단하도록 넘긴다.
     */
    private static void inflate(DocumentEntry document, InflationBudget budget, boolean encrypted) {
        Inflater inflater = new Inflater(true);
        try (InputStream raw = open(document, encrypted);
             InputStream in = new InflaterInputStream(raw, inflater)) {
            budget.consume(document.getName(), in);
        } catch (IOException e) {
            log.debug("HWP 스트림 {} 은 압축되지 않았거나 손상됨: {}", document.getName(), e.getMessage());
        } finally {
            inflater.end();
        }
    }

    private static InputStream open(DocumentEntry document, boolean encrypted) throws IOException {
        if (!encrypted) {
            return new DocumentInputStream(document);
        }
        if (DISTRIBUTION_STREAM == null) {
            log.error("hwplib 배포용 문서 복호화 메서드를 찾지 못해 배포용 HWP 를 검사할 수 없습니다.");
            throw new ChatException(ChatErrorCode.FILE_PARSE_FAILED);
        }
        try {
            return (InputStream) DISTRIBUTION_STREAM.invoke(null, document, true);
        } catch (InvocationTargetException e) {
            throw new IOException("배포용 스트림 복호화 실패", e.getCause());
        } catch (IllegalAccessException e) {
            throw new IOException("배포용 스트림 복호화 실패", e);
        }
    }

    private static Method resolveDistributionStream() {
        try {
            Method method = StreamReader.class.getDeclaredMethod(
                "documentInputStream", DocumentEntry.class, boolean.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("hwplib 배포용 문서 복호화 메서드를 찾지 못했습니다: {}", e.getMessage());
            return null;
        }
    }
}

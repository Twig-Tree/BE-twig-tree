package com.tree.twig_tree.domain.chat.parser;

import com.tree.twig_tree.domain.chat.exception.ChatException;
import com.tree.twig_tree.domain.chat.exception.code.ChatErrorCode;
import kr.dogfoot.hwplib.object.fileheader.FileHeader;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DirectoryEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentEntry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.DocumentInputStream;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.Entry;
import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.POIFSFileSystem;
import kr.dogfoot.hwplib.reader.ForFileHeader;
import kr.dogfoot.hwplib.util.compoundFile.reader.StreamReader;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
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
 * <p>배포용 문서는 일부 스트림이 AES 로 암호화된 뒤 압축되어 있다. hwplib 1.1.10 은 파일 헤더의
 * 배포용 플래그가 켜져 있으면 {@code ViewText/Section*} 와 {@code Scripts/DefaultJScript} ·
 * {@code Scripts/JScriptVersion} 을 복호화한 뒤 푼다. 암호문 그대로 풀어보면 형식 오류로 끝나 크기를
 * 재지 못하므로, 이 스트림들은 같은 방식으로 복호화한 뒤 검사한다. 배포용 여부와 복호화는 hwplib
 * 과 똑같이 판단해야 하므로 hwplib 코드를 그대로 쓰고, 복호화 메서드는 private 이라 리플렉션으로
 * 호출한다. 라이브러리 업그레이드로 메서드를 찾지 못하면 배포용 문서는 검사할 수 없으니 받지 않는다.
 */
@Slf4j
final class HwpOleGuard {

    private static final String FILE_HEADER = "FileHeader";

    /** 배포용 문서의 본문 스토리지. 이 아래 스트림은 모두 암호화되어 있다. */
    private static final String VIEW_TEXT = "ViewText";

    /** 스크립트 스토리지. 배포용 문서에서는 이 아래 두 스트림이 암호화되어 있다. */
    private static final String SCRIPTS = "Scripts";
    private static final Set<String> ENCRYPTED_SCRIPTS = Set.of("DefaultJScript", "JScriptVersion");

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
            DirectoryEntry root = fs.getRoot();
            walk(root, new InflationBudget(), isDistribution(root), 0);
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

    /**
     * 파일 헤더의 배포용 플래그. hwplib 과 같은 코드로 읽는다.
     * 헤더가 없거나 읽을 수 없으면 hwplib 도 파싱에 실패하므로, 일반 문서로 보고 검사만 계속한다.
     */
    private static boolean isDistribution(DirectoryEntry root) {
        try {
            if (!root.hasEntry(FILE_HEADER)) {
                return false;
            }
            DocumentEntry entry = (DocumentEntry) root.getEntry(FILE_HEADER);
            StreamReader reader = StreamReader.create(entry, false, false, null);
            try {
                FileHeader header = new FileHeader();
                ForFileHeader.read(header, reader);
                return header.isDistribution();
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            log.debug("HWP 파일 헤더 읽기 실패: {}", e.toString());
            return false;
        }
    }

    /** {@code depth} 는 루트가 0 이다. hwplib 은 루트 바로 아래 스토리지만 읽는다. */
    private static void walk(DirectoryEntry directory, InflationBudget budget, boolean distribution, int depth) {
        for (Entry entry : directory) {
            if (entry instanceof DirectoryEntry child) {
                walk(child, budget, distribution, depth + 1);
            } else if (entry instanceof DocumentEntry document) {
                boolean encrypted = distribution && depth == 1 && isEncryptedStream(directory.getName(), document.getName());
                inflate(document, budget, encrypted);
            }
        }
    }

    /** 배포용 문서에서 hwplib 이 복호화한 뒤 푸는 스트림인지. */
    private static boolean isEncryptedStream(String storage, String stream) {
        return VIEW_TEXT.equals(storage)
            || (SCRIPTS.equals(storage) && ENCRYPTED_SCRIPTS.contains(stream));
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

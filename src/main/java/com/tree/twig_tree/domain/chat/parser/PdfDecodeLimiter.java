package com.tree.twig_tree.domain.chat.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSStream;
import org.apache.pdfbox.filter.DecodeOptions;
import org.apache.pdfbox.filter.DecodeResult;
import org.apache.pdfbox.filter.Filter;
import org.apache.pdfbox.filter.FilterFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * PDFBox 의 스트림 디코딩 결과 크기에 상한을 둔다.
 *
 * <p>PDF 는 zip 처럼 컨테이너를 미리 풀어볼 수 없다. 스트림마다 필터가 체인으로 걸리고, xref 스트림 ·
 * 객체 스트림 · 페이지 본문이 각각 PDFBox 안에서 필요할 때 디코딩된다. PDFBox 는 디코딩 결과를
 * 크기 제한 없는 메모리 버퍼에 쌓으므로, 모든 디코딩이 거쳐가는 {@link FilterFactory} 의 필터를
 * 바이트 수를 세는 래퍼로 바꿔 끼운다. 공개 API 로는 바꿀 수 없어 리플렉션을 쓴다.
 *
 * <p>상한은 디코딩 한 번 단위로 건다. 같은 폼 XObject 가 페이지마다 다시 디코딩되는 정상 문서가
 * 있어 누적 크기로는 메모리 사용량을 가늠할 수 없기 때문이다. 필터 체인의 중간 결과도 각각 센다.
 *
 * <p>단, 페이지 본문이 FlateDecode 스트림 하나뿐이면 PDFBox 는 필터를 거치지 않고 직접 풀면서
 * 토큰 단위로 파싱한다. 결과를 쌓지는 않지만 거대한 토큰 하나는 그대로 메모리에 올라가므로,
 * {@link #verifyPageContents(PDDocument)} 로 이 경로의 스트림을 미리 한 번 풀어본다. 이 사전 검사는
 * 페이지 본문 전체의 작업량에도 문서별 상한을 건다.
 *
 * <p>PDFBox 는 일부 디코딩 오류(폰트 등)를 삼키고 계속 진행하므로, 상한 초과는 예외와 별개로
 * 스레드별 플래그에도 남긴다. 파서는 추출이 끝난 뒤 이 플래그를 확인한다.
 */
@Slf4j
final class PdfDecodeLimiter {

    private static final ThreadLocal<Boolean> EXCEEDED = ThreadLocal.withInitial(() -> false);

    private static final boolean INSTALLED = install();

    private PdfDecodeLimiter() {
    }

    /** 래퍼가 정상적으로 끼워졌는지. 끼워지지 않았으면 PDF 는 받지 않는다. */
    static boolean isInstalled() {
        return INSTALLED;
    }

    /** 이번 스레드의 상한 초과 기록을 지운다. 파싱 시작 전에 호출한다. */
    static void reset() {
        EXCEEDED.remove();
    }

    /** 마지막 {@link #reset()} 이후 이 스레드에서 디코딩 상한을 넘은 적이 있는지. */
    static boolean exceeded() {
        return EXCEEDED.get();
    }

    /**
     * 페이지 본문 전체를 디코딩했을 때의 바이트 상한. 페이지가 같은 스트림을 참조하면 참조할 때마다 센다.
     * 텍스트 추출 단계도 페이지마다 본문을 다시 풀기 때문이다.
     */
    static final long MAX_PAGE_CONTENT_BYTES = InflationBudget.MAX_TOTAL_BYTES;

    /**
     * 사전 검사 결과.
     *
     * @param decodedBytes 이 검사에서 실제로 풀어낸 바이트. 같은 스트림은 한 번만 푼다.
     * @param chargedBytes 페이지 참조마다 센 바이트. 텍스트 추출 단계의 페이지 본문 작업량과 같다.
     */
    record PageContentScan(long decodedBytes, long chargedBytes) {
    }

    /**
     * 페이지 본문 스트림을 래핑된 필터로 끝까지 풀어보며 문서 전체의 작업량을 센다.
     * 풀어낸 바이트는 버리므로 메모리는 거의 쓰지 않는다.
     *
     * <ul>
     *   <li>같은 스트림은 처음 한 번만 풀고, 다시 참조되면 기억해 둔 크기만 더한다.</li>
     *   <li>새로 푸는 스트림은 남은 예산까지만 풀어, 검사 자체의 작업량도 상한 안에 묶는다.</li>
     *   <li>본문이 스트림 배열이어도 PDFBox 는 페이지마다 다시 풀므로 같은 방식으로 센다.</li>
     * </ul>
     *
     * @throws DecodeLimitExceededException 스트림 하나 또는 문서 전체의 디코딩 크기가 상한을 넘으면
     */
    static PageContentScan verifyPageContents(PDDocument document) throws IOException {
        Map<COSStream, Long> decodedSizes = new IdentityHashMap<>();
        long decoded = 0;
        long charged = 0;
        for (PDPage page : document.getPages()) {
            for (COSStream stream : contentStreams(page)) {
                Long size = decodedSizes.get(stream);
                if (size == null) {
                    size = decodedSize(stream, MAX_PAGE_CONTENT_BYTES - charged);
                    decodedSizes.put(stream, size);
                    decoded += size;
                }
                charged += size;
                if (charged > MAX_PAGE_CONTENT_BYTES) {
                    throw exceed();
                }
            }
        }
        return new PageContentScan(decoded, charged);
    }

    /** 페이지 본문은 스트림 하나이거나 스트림 배열이다. 그 밖의 값은 PDFBox 도 본문으로 읽지 않는다. */
    private static List<COSStream> contentStreams(PDPage page) {
        COSBase contents = page.getCOSObject().getDictionaryObject(COSName.CONTENTS);
        if (contents instanceof COSStream stream) {
            return List.of(stream);
        }
        List<COSStream> streams = new ArrayList<>();
        if (contents instanceof COSArray array) {
            for (int i = 0; i < array.size(); i++) {
                if (array.getObject(i) instanceof COSStream stream) {
                    streams.add(stream);
                }
            }
        }
        return streams;
    }

    /**
     * 스트림을 {@code limit} 바이트까지만 풀어 디코딩 크기를 잰다.
     * 필터가 하나면 결과를 버리는 출력으로 바로 흘려 메모리를 쓰지 않는다. 필터 체인은 중간 결과가
     * 필요해 PDFBox 디코딩을 그대로 쓰며, 이때도 디코딩 한 번의 상한은 래퍼가 건다.
     */
    private static long decodedSize(COSStream stream, long limit) throws IOException {
        LimitedOutputStream sink = new LimitedOutputStream(OutputStream.nullOutputStream(), limit);
        COSBase filters = stream.getFilters();
        if (filters instanceof COSName name) {
            Filter filter = FilterFactory.INSTANCE.getFilter(name);
            try (InputStream raw = stream.createRawInputStream()) {
                filter.decode(raw, sink, stream, 0);
            }
        } else {
            try (InputStream in = filters == null ? stream.createRawInputStream() : stream.createInputStream()) {
                in.transferTo(sink);
            }
        }
        return sink.written;
    }

    private static DecodeLimitExceededException exceed() {
        EXCEEDED.set(true);
        return new DecodeLimitExceededException();
    }

    @SuppressWarnings("unchecked")
    private static synchronized boolean install() {
        try {
            Field field = FilterFactory.class.getDeclaredField("filters");
            field.setAccessible(true);
            Map<COSName, Filter> filters = (Map<COSName, Filter>) field.get(FilterFactory.INSTANCE);
            filters.replaceAll((name, filter) ->
                filter instanceof LimitedFilter ? filter : new LimitedFilter(filter));
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.error("PDFBox 디코딩 상한을 설치하지 못했습니다: {}", e.getMessage());
            return false;
        }
    }

    /** 디코딩만 가로채고 인코딩은 원래 필터에 그대로 맡긴다. */
    static final class LimitedFilter extends Filter {

        private final Filter delegate;

        private LimitedFilter(Filter delegate) {
            this.delegate = delegate;
        }

        @Override
        public DecodeResult decode(InputStream encoded, OutputStream decoded, COSDictionary parameters,
                                   int index) throws IOException {
            return delegate.decode(encoded, perDecode(decoded), parameters, index);
        }

        @Override
        public DecodeResult decode(InputStream encoded, OutputStream decoded, COSDictionary parameters,
                                   int index, DecodeOptions options) throws IOException {
            return delegate.decode(encoded, perDecode(decoded), parameters, index, options);
        }

        private static OutputStream perDecode(OutputStream decoded) {
            return new LimitedOutputStream(decoded, InflationBudget.MAX_ENTRY_BYTES);
        }

        @Override
        protected void encode(InputStream input, OutputStream encoded, COSDictionary parameters)
            throws IOException {
            delegate.encode(input, encoded, parameters, 0);
        }
    }

    /** 상한을 넘는 쓰기가 들어오면 쓰기 전에 끊는다. */
    private static final class LimitedOutputStream extends FilterOutputStream {

        private final long limit;
        private long written;

        private LimitedOutputStream(OutputStream out, long limit) {
            super(out);
            this.limit = limit;
        }

        @Override
        public void write(int b) throws IOException {
            reserve(1);
            out.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            reserve(len);
            out.write(b, off, len);
        }

        private void reserve(int len) throws IOException {
            written += len;
            if (written > limit) {
                throw exceed();
            }
        }
    }

    static final class DecodeLimitExceededException extends IOException {
        private DecodeLimitExceededException() {
            super("PDF 스트림 디코딩 크기가 상한을 초과했습니다.");
        }
    }
}

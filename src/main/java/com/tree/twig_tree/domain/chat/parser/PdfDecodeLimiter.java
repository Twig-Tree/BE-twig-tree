package com.tree.twig_tree.domain.chat.parser;

import lombok.extern.slf4j.Slf4j;
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
 * {@link #verifyPageContents(PDDocument)} 로 이 경로의 스트림을 미리 한 번 풀어본다.
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
     * 필터를 거치지 않고 디코딩되는 페이지 본문 스트림을 래핑된 필터로 끝까지 풀어본다.
     * 풀어낸 바이트는 버리므로 메모리는 거의 쓰지 않는다.
     *
     * @throws DecodeLimitExceededException 디코딩 크기가 상한을 넘으면
     */
    static void verifyPageContents(PDDocument document) throws IOException {
        for (PDPage page : document.getPages()) {
            COSBase contents = page.getCOSObject().getDictionaryObject(COSName.CONTENTS);
            if (contents instanceof COSStream stream && COSName.FLATE_DECODE.equals(stream.getFilters())) {
                drain(stream);
            }
        }
    }

    private static void drain(COSStream stream) throws IOException {
        Filter flate = FilterFactory.INSTANCE.getFilter(COSName.FLATE_DECODE);
        try (InputStream raw = stream.createRawInputStream()) {
            flate.decode(raw, OutputStream.nullOutputStream(), stream, 0);
        }
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
            return delegate.decode(encoded, new LimitedOutputStream(decoded), parameters, index);
        }

        @Override
        public DecodeResult decode(InputStream encoded, OutputStream decoded, COSDictionary parameters,
                                   int index, DecodeOptions options) throws IOException {
            return delegate.decode(encoded, new LimitedOutputStream(decoded), parameters, index, options);
        }

        @Override
        protected void encode(InputStream input, OutputStream encoded, COSDictionary parameters)
            throws IOException {
            delegate.encode(input, encoded, parameters, 0);
        }
    }

    /** 상한을 넘는 쓰기가 들어오면 쓰기 전에 끊는다. */
    private static final class LimitedOutputStream extends FilterOutputStream {

        private long written;

        private LimitedOutputStream(OutputStream out) {
            super(out);
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
            if (written > InflationBudget.MAX_ENTRY_BYTES) {
                EXCEEDED.set(true);
                throw new DecodeLimitExceededException();
            }
        }
    }

    static final class DecodeLimitExceededException extends IOException {
        private DecodeLimitExceededException() {
            super("PDF 스트림 디코딩 크기가 상한을 초과했습니다.");
        }
    }
}

package com.tree.twig_tree.domain.chat.parser;

import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.POIFSFileSystem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 압축 해제 폭탄 샘플을 코드로 만든다.
 *
 * <p>0 으로 채운 바이트는 압축률이 1:1000 가까이 나와서, 수십 MB 로 풀리는 샘플도 수십 KB 로 만들어진다.
 * 테스트가 실제 악성 파일을 저장소에 들고 있지 않도록 매번 생성한다.
 */
final class MaliciousSamples {

    static final long MB = 1024L * 1024L;

    /** 엔트리 하나 상한(50MB)을 넘는 크기. */
    static final long OVER_ENTRY_LIMIT = InflationBudget.MAX_ENTRY_BYTES + MB;

    private MaliciousSamples() {
    }

    /** 이름이 {@code entry0}, {@code entry1} … 인 엔트리를 주어진 크기의 0 바이트로 채운 zip. */
    static byte[] zipOfZeros(long... entrySizes) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.setLevel(Deflater.BEST_COMPRESSION);
            for (int i = 0; i < entrySizes.length; i++) {
                zip.putNextEntry(new ZipEntry("entry" + i));
                writeZeros(zip, entrySizes[i]);
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("테스트용 zip 생성 실패", e);
        }
    }

    /** 빈 엔트리만 {@code count} 개 들어있는 zip. */
    static byte[] zipWithEntries(int count) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (int i = 0; i < count; i++) {
                zip.putNextEntry(new ZipEntry("e" + i));
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("테스트용 zip 생성 실패", e);
        }
    }

    /** {@code size} 바이트의 0 을 hwplib 과 같은 raw deflate 로 압축한 바이트. */
    static byte[] rawDeflatedZeros(long size) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(out, deflater)) {
                writeZeros(deflate, size);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("테스트용 deflate 생성 실패", e);
        } finally {
            deflater.end();
        }
    }

    /** {@code BodyText/Section0} 스트림이 {@code size} 바이트로 풀리는 OLE 복합문서. */
    static byte[] hwpWithInflatedSection(long size) {
        try {
            POIFSFileSystem fs = new POIFSFileSystem();
            fs.createDirectory("BodyText")
                .createDocument("Section0", new ByteArrayInputStream(rawDeflatedZeros(size)));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("테스트용 OLE 생성 실패", e);
        }
    }

    private static void writeZeros(OutputStream out, long size) throws IOException {
        byte[] chunk = new byte[64 * 1024];
        long remaining = size;
        while (remaining > 0) {
            int len = (int) Math.min(chunk.length, remaining);
            out.write(chunk, 0, len);
            remaining -= len;
        }
    }
}

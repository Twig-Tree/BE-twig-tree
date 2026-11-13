package com.tree.twig_tree.domain.chat.parser;

import kr.dogfoot.hwplib.org.apache.poi.poifs.filesystem.POIFSFileSystem;
import kr.dogfoot.hwplib.util.binary.Obfuscation;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;
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

    /**
     * {@code Scripts/<scriptName>} 스트림이 {@code size} 바이트로 풀리는 OLE 복합문서.
     *
     * <p>{@code distribution} 이면 파일 헤더에 배포용 플래그를 켜고, 스크립트를 hwplib 1.1.10 의
     * 배포용 형식(레코드 헤더 4바이트 + 난독화된 256바이트 + AES/ECB 암호문)으로 암호화한다.
     * 아니면 raw deflate 만 한다.
     */
    static byte[] hwpWithInflatedScript(String scriptName, long size, boolean distribution) {
        try {
            byte[] compressed = rawDeflatedZeros(size);
            POIFSFileSystem fs = new POIFSFileSystem();
            fs.createDocument(new ByteArrayInputStream(hwpFileHeader(distribution)), "FileHeader");
            fs.createDirectory("Scripts").createDocument(scriptName,
                new ByteArrayInputStream(distribution ? encryptForDistribution(compressed) : compressed));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("테스트용 OLE 생성 실패", e);
        }
    }

    /** 압축 플래그를 켠 HWP 5.0 파일 헤더(256바이트). */
    private static byte[] hwpFileHeader(boolean distribution) {
        ByteBuffer header = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN);
        header.put("HWP Document File".getBytes(StandardCharsets.US_ASCII));
        header.position(32);
        header.putInt(0x05000300);                               // 버전 5.0.3.0
        header.putInt(COMPRESSED | (distribution ? DISTRIBUTION : 0));
        return header.array();
    }

    private static byte[] encryptForDistribution(byte[] payload) {
        byte[] data = new byte[256];
        new Random(38).nextBytes(data);                          // 앞 4바이트가 난독화 시드가 된다
        int keyOffset = 4 + (data[0] & 0x0F);
        byte[] key = Arrays.copyOfRange(data, keyOffset, keyOffset + 16);

        // 난독화는 시드로 만든 값과 XOR 하는 변환이라, 한 번 더 적용하면 원래대로 돌아간다.
        byte[] obfuscated = data.clone();
        Obfuscation.transform(obfuscated);

        try {
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"));
            ByteBuffer stream = ByteBuffer.allocate(4 + 256 + payload.length + 16).order(ByteOrder.LITTLE_ENDIAN);
            stream.putInt(HWPTAG_DISTRIBUTE_DOC_DATA | (256 << 20));
            stream.put(obfuscated);
            stream.put(cipher.doFinal(payload));
            return Arrays.copyOf(stream.array(), stream.position());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("테스트용 배포용 스트림 암호화 실패", e);
        }
    }

    private static final int COMPRESSED = 1;
    private static final int DISTRIBUTION = 1 << 2;
    private static final int HWPTAG_DISTRIBUTE_DOC_DATA = 0x10 + 12;

    /**
     * 소형 스트림 {@code DocInfo} 의 크기는 그대로 두고, 소형 스트림이 담기는 루트 엔트리의 블록 체인만
     * 비운 OLE 복합문서. 스트림을 여는 순간 POIFS 가 배열 범위 예외를 던진다.
     */
    static byte[] hwpWithEmptyMiniStreamChain() {
        try {
            POIFSFileSystem fs = new POIFSFileSystem();
            fs.createDocument(new ByteArrayInputStream(new byte[100]), "DocInfo");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            byte[] bytes = out.toByteArray();

            // 헤더(512바이트) 바로 뒤가 첫 디렉터리 섹터이고, 그 첫 엔트리(128바이트)가 루트 엔트리다.
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            int rootEntry = 512;
            buffer.putInt(rootEntry + 0x74, END_OF_CHAIN);  // 시작 블록
            buffer.putInt(rootEntry + 0x78, 0);             // 소형 스트림 컨테이너 크기
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("테스트용 OLE 생성 실패", e);
        }
    }

    private static final int END_OF_CHAIN = -2;

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

package com.example.docmind.service;

import com.example.docmind.config.UploadProperties;
import com.example.docmind.exception.UnsupportedFileTypeException;
import org.apache.tika.Tika;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Set;

/**
 * Validate file đầu vào: nhận diện MIME theo magic-byte (Tika, KHÔNG theo đuôi file)
 * + tính SHA-256. Hỗ trợ đọc danh sách MIME cho phép từ {@link UploadProperties}.
 */
public final class FileValidation {

    private static final Tika TIKA = new Tika();

    /** Danh sách MIME được hỗ trợ mặc định (khớp các loại parser xử lý). */
    public static final Set<String> DEFAULT_ALLOWED_MIME = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "application/vnd.openxmlformats-officedocument.presentationml.presentation", // .pptx
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", // .xlsx
            "text/html",
            "text/markdown",
            "text/x-web-markdown",
            "text/x-markdown",
            "text/plain",
            "image/png",
            "image/jpeg"
    );

    private FileValidation() {
    }

    /** Nhận diện MIME theo nội dung (magic-byte), có xét thêm filename như gợi ý phụ. */
    public static String detectMime(byte[] bytes, String filename) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            String mime = TIKA.detect(in, filename);
            return mime == null ? "application/octet-stream" : mime;
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được bytes để detect MIME", e);
        }
    }

    /** Nhận diện MIME chỉ theo magic-byte (không dùng filename) — dùng cho test đối chiếu. */
    public static String detectMime(byte[] bytes) {
        return TIKA.detect(bytes);
    }

    /** Ném {@link UnsupportedFileTypeException} nếu MIME không nằm trong allow-list mặc định. */
    public static void requireSupported(String mime) {
        requireSupported(mime, DEFAULT_ALLOWED_MIME);
    }

    /** Ném {@link UnsupportedFileTypeException} nếu MIME không nằm trong allow-list cấu hình. */
    public static void requireSupported(String mime, Collection<String> allowedMimes) {
        Collection<String> effective = (allowedMimes != null && !allowedMimes.isEmpty())
                ? allowedMimes
                : DEFAULT_ALLOWED_MIME;
        if (!effective.contains(mime)) {
            throw new UnsupportedFileTypeException(
                    "Loại file không được hỗ trợ (magic-byte MIME=" + mime + ")");
        }
    }

    public static boolean isSupported(String mime) {
        return DEFAULT_ALLOWED_MIME.contains(mime);
    }

    /** SHA-256 hex của raw bytes. */
    public static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(bytes);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JVM thiếu SHA-256", e);
        }
    }
}

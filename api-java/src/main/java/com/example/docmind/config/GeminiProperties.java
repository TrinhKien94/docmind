package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cấu hình Gemini. Để trống {@code gemini.api.keys} => chat dùng LLM stub hoặc 9Router.
 * Chỉ khi có ít nhất 1 key và 9Router tắt, GeminiLlmClient mới được kích hoạt.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    /** Danh sách API key (round-robin). Trống => stub. */
    private Api api = new Api();

    private String model = "gemini-2.5-flash-lite";

    private String baseUrl = "https://generativelanguage.googleapis.com/v1beta";

    /** Đường dẫn endpoint generateContent của Gemini API. */
    private String generatePathTemplate = "/models/{model}:generateContent?key={key}";

    /** Timeout kết nối (ms) khi gọi Gemini. */
    private int connectTimeoutMs = 10000;

    /** Timeout đọc phản hồi (ms) khi gọi Gemini. */
    private int readTimeoutMs = 60000;

    @Getter
    @Setter
    public static class Api {
        /** Spring tự tách chuỗi CSV "k1,k2" thành List. */
        private List<String> keys = List.of();
    }

    /** True khi có ít nhất 1 key không rỗng. */
    public boolean hasKey() {
        return api.getKeys() != null
                && api.getKeys().stream().anyMatch(k -> k != null && !k.isBlank());
    }
}

package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Cấu hình CORS cho phép tuỳ chỉnh qua {@code application.properties} / biến môi trường.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "docmind.cors")
public class CorsProperties {

    /** Đường dẫn áp dụng CORS. */
    private String pathPattern = "/api/**";

    /** Danh sách mẫu Origin được phép gọi API. */
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of("*"));

    /** Danh sách HTTP methods được phép. */
    private List<String> allowedMethods = new ArrayList<>(List.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS"
    ));

    /** Danh sách headers được phép gửi lên. */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));

    /** Danh sách headers phía trình duyệt được phép đọc. */
    private List<String> exposedHeaders = new ArrayList<>(List.of("X-Correlation-Id"));

    /** Cho phép gửi credentials (cookie/authorization) qua CORS. */
    private boolean allowCredentials = false;

    /** Thời gian cache preflight request (giây). */
    private long maxAgeSeconds = 3600;
}

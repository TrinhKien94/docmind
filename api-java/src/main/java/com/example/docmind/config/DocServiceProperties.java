package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình client tới doc-service-py (parser Python). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.doc-service")
public class DocServiceProperties {

    /** Base URL của parser, mặc định http://localhost:8091. */
    private String baseUrl = "http://localhost:8091";

    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 60000;

    /** Chu kỳ poll job (ms) khi chờ parse. */
    private long pollIntervalMs = 1500;

    /** Số lần poll tối đa trước khi coi là timeout. */
    private int pollMaxAttempts = 40;

    /** Đường dẫn endpoint gửi file phân tích. */
    private String parseEndpointPath = "/v1/parse";

    /** Đường dẫn endpoint kiểm tra trạng thái job. */
    private String jobStatusEndpointPath = "/v1/jobs/{jobId}";

    /** Đường dẫn endpoint lấy kết quả phân tích job. */
    private String jobResultEndpointPath = "/v1/jobs/{jobId}/result";
}

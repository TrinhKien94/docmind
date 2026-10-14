package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Cấu hình embedding. dim=384 khớp paraphrase-multilingual-MiniLM-L12-v2. */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.embedding")
public class EmbeddingProperties {

    private int dim = 384;

    /** Cấu hình gọi embedding thật qua doc-service-py (POST /v1/embed). */
    private Remote remote = new Remote();

    /** Nhóm key docmind.embedding.remote.* */
    @Getter
    @Setter
    public static class Remote {

        /** Bật/tắt remote embedding; false => chỉ dùng stub. */
        private boolean enabled = true;

        /** Đường dẫn endpoint embed trên doc-service-py. */
        private String endpointPath = "/v1/embed";

        /**
         * Thời gian ngắt mạch (circuit-breaker backoff, ms) khi doc-service-py không phản hồi
         * để chế độ Standalone rơi về StubEmbeddingClient tức thì mà không lặp lại kết nối lỗi.
         */
        private long failureBackoffMs = 60_000L;
    }
}

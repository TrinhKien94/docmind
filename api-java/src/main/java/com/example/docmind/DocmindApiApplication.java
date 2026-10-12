package com.example.docmind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * DocMind api-java — Spring Boot 4 orchestrator.
 *
 * <p>Hai luồng chính:
 * <ul>
 *   <li>Ingest: nhận file upload, validate (Tika magic-byte + SHA-256), gọi doc-service-py
 *       (REST client propagate X-Correlation-Id) để parse ra canonical blocks, rồi persist +
 *       chunk + embed.</li>
 *   <li>Chat/RAG: luồng CHỈ-JAVA (không bao giờ gọi Python) — hybrid retrieval (pgvector cosine
 *       + tsvector full-text) fuse bằng RRF, build prompt, gọi LLM qua interface, trả answer +
 *       citations truy ngược về block/page/bbox/heading_path.</li>
 * </ul>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
public class DocmindApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocmindApiApplication.class, args);
    }
}

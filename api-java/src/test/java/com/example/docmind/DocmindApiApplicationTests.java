package com.example.docmind;

import com.example.docmind.service.embedding.EmbeddingClient;
import com.example.docmind.service.embedding.RemoteEmbeddingClient;
import com.example.docmind.service.llm.LlmClient;
import com.example.docmind.service.llm.StubLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-load test: toàn bộ wiring khởi động KHÔNG cần Postgres thật, KHÔNG cần model.
 * LLM vẫn stub (gemini.api.keys trống). Embedding giờ là {@link RemoteEmbeddingClient}
 * (@Primary) — gọi doc-service-py qua REST, fallback stub theo từng call khi lỗi;
 * bean không mở kết nối lúc khởi động nên context load vẫn không cần service sống.
 * Stub fallback được dựng bên trong remote client (không là bean riêng); dim giữ 384.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DocmindApiApplicationTests {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Test
    void contextLoadsWithRemoteEmbeddingPrimaryByDefault() {
        assertThat(llmClient).isInstanceOf(StubLlmClient.class);
        // @Primary remote client là bean được inject mặc định.
        assertThat(embeddingClient).isInstanceOf(RemoteEmbeddingClient.class);
        // dim nhất quán 384 (khớp pgvector + EmbeddingProperties).
        assertThat(embeddingClient.dim()).isEqualTo(384);
    }
}

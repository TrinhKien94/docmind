package com.example.docmind;

import com.example.docmind.service.embedding.EmbeddingClient;
import com.example.docmind.service.embedding.StubEmbeddingClient;
import com.example.docmind.service.llm.LlmClient;
import com.example.docmind.service.llm.StubLlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Context-load test: toàn bộ wiring khởi động KHÔNG cần Postgres thật, KHÔNG cần model.
 * Verify các impl stub (LLM + embedding) active mặc định (gemini.api.keys trống).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class DocmindApiApplicationTests {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private EmbeddingClient embeddingClient;

    @Test
    void contextLoadsWithStubClientsActiveByDefault() {
        assertThat(llmClient).isInstanceOf(StubLlmClient.class);
        assertThat(embeddingClient).isInstanceOf(StubEmbeddingClient.class);
        assertThat(embeddingClient.dim()).isEqualTo(384);
    }
}

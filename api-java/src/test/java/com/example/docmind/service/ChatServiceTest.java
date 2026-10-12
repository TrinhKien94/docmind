package com.example.docmind.service;

import com.example.docmind.dto.ChatRequest;
import com.example.docmind.dto.ChatResponse;
import com.example.docmind.repository.RetrievedChunk;
import com.example.docmind.service.llm.StubLlmClient;
import com.example.docmind.service.retrieval.ReciprocalRankFusion;
import com.example.docmind.service.retrieval.RetrievalService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test luồng chat Java-only: RetrievalService (mock) + StubLlmClient (thật).
 * Verify answer + citations truy ngược block/page/bbox/heading_path, và citation
 * đều hợp lệ (trỏ về chunk đã retrieve).
 */
class ChatServiceTest {

    @Test
    void chatProducesAnswerAndCitationsFromRetrievedChunks() {
        RetrievalService retrievalService = mock(RetrievalService.class);
        StubLlmClient llm = new StubLlmClient();
        ChatService chatService = new ChatService(retrievalService, llm);

        UUID chunkId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        RetrievedChunk rc = new RetrievedChunk(
                chunkId, docId,
                List.of("Chương 1", "Đạo hàm"),
                List.of(blockId),
                2, new double[]{72.0, 90.0, 480.0, 118.0},
                "Đạo hàm đo tốc độ thay đổi tức thời.", 0.95);

        when(retrievalService.retrieve(anyList(), any(), any()))
                .thenReturn(List.of(new ReciprocalRankFusion.FusedChunk(rc, 0.5)));

        ChatRequest req = new ChatRequest(List.of(docId.toString()), "Đạo hàm là gì?", 5);
        ChatResponse resp = chatService.chat(req);

        // Answer từ stub LLM (tất định, có echo câu hỏi).
        assertThat(resp.answer()).contains("stub-llm");
        assertThat(resp.answer()).contains("Đạo hàm là gì?");

        // Citation truy ngược về chunk/document/block/page/bbox/heading_path.
        assertThat(resp.citations()).hasSize(1);
        var cit = resp.citations().get(0);
        assertThat(cit.chunkId()).isEqualTo(chunkId.toString());
        assertThat(cit.documentId()).isEqualTo(docId.toString());
        assertThat(cit.blockIds()).containsExactly(blockId.toString());
        assertThat(cit.page()).isEqualTo(2);
        assertThat(cit.bbox()).containsExactly(72.0, 90.0, 480.0, 118.0);
        assertThat(cit.headingPath()).containsExactly("Chương 1", "Đạo hàm");
        assertThat(cit.snippet()).contains("Đạo hàm");
    }
}

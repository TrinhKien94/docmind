package com.example.docmind.service.retrieval;

import com.example.docmind.config.ChatProperties;
import com.example.docmind.repository.ChunkRepository;
import com.example.docmind.repository.RetrievedChunk;
import com.example.docmind.service.embedding.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Hybrid retrieval (Java-only): kết hợp vector search (pgvector cosine) + full-text
 * (tsvector) rồi fuse bằng RRF. KHÔNG BAO GIỜ gọi doc-service-py.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final ChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final ChatProperties chatProps;

    public RetrievalService(ChunkRepository chunkRepository,
                            EmbeddingClient embeddingClient,
                            ChatProperties chatProps) {
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.chatProps = chatProps;
    }

    /**
     * Chạy 2 arm retrieval, fuse RRF, trả top-K FusedChunk.
     *
     * @param topK số kết quả cuối (nếu null dùng config).
     */
    @Transactional(readOnly = true)
    public List<ReciprocalRankFusion.FusedChunk> retrieve(List<UUID> documentIds, String question, Integer topK) {
        int candidateK = chatProps.getCandidateK();
        int finalK = topK != null ? topK : chatProps.getTopK();

        // Arm 1: vector search.
        float[] queryEmbedding = embeddingClient.embed(question);
        List<RetrievedChunk> vectorHits = chunkRepository.searchByVector(documentIds, queryEmbedding, candidateK);

        // Arm 2: full-text search.
        List<RetrievedChunk> textHits = chunkRepository.searchByFullText(documentIds, question, candidateK);

        log.info("stage=retrieve vector_candidates={} fulltext_candidates={} candidate_k={}",
                vectorHits.size(), textHits.size(), candidateK);

        // Fuse bằng RRF.
        List<ReciprocalRankFusion.FusedChunk> fused =
                ReciprocalRankFusion.fuse(List.of(vectorHits, textHits), chatProps.getRrfK());

        List<ReciprocalRankFusion.FusedChunk> top =
                fused.size() > finalK ? fused.subList(0, finalK) : fused;

        log.info("stage=rerank fused_total={} returned={} rrf_k={}",
                fused.size(), top.size(), chatProps.getRrfK());
        return top;
    }
}

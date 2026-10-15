package com.example.docmind.service.retrieval;

import com.example.docmind.config.ChatProperties;
import com.example.docmind.repository.ChunkRepository;
import com.example.docmind.repository.RetrievedChunk;
import com.example.docmind.service.embedding.EmbeddingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Hybrid retrieval (Java-only): kết hợp vector search (pgvector cosine / in-memory cosine)
 * + full-text (tsvector / Vietnamese BM25) rồi fuse bằng RRF. KHÔNG BAO GIỜ gọi doc-service-py.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final ChunkRepository chunkRepository;
    private final EmbeddingClient embeddingClient;
    private final ChatProperties chatProps;
    private final LocalKnowledgeBaseService localKnowledgeBaseService;
    private volatile long dbUnavailableUntilEpochMs = 0L;

    public RetrievalService(ChunkRepository chunkRepository,
                            EmbeddingClient embeddingClient,
                            ChatProperties chatProps) {
        this(chunkRepository, embeddingClient, chatProps, null);
    }

    @Autowired
    public RetrievalService(ChunkRepository chunkRepository,
                            EmbeddingClient embeddingClient,
                            ChatProperties chatProps,
                            LocalKnowledgeBaseService localKnowledgeBaseService) {
        this.chunkRepository = chunkRepository;
        this.embeddingClient = embeddingClient;
        this.chatProps = chatProps != null ? chatProps : new ChatProperties();
        this.localKnowledgeBaseService = localKnowledgeBaseService;
    }

    /**
     * Chạy 2 arm retrieval, fuse RRF, trả top-K FusedChunk.
     *
     * @param topK số kết quả cuối (nếu null dùng config).
     */
    public List<ReciprocalRankFusion.FusedChunk> retrieve(List<UUID> documentIds, String question, Integer topK) {
        int candidateK = chatProps.getCandidateK();
        int finalK = topK != null ? topK : chatProps.getTopK();

        float[] queryEmbedding = embeddingClient.embed(question);
        List<RetrievedChunk> vectorHits = List.of();
        List<RetrievedChunk> textHits = List.of();
        boolean usedLocalKb = false;

        long now = System.currentTimeMillis();
        boolean canSkipDeadDb = localKnowledgeBaseService != null
                && localKnowledgeBaseService.hasDocuments()
                && now < dbUnavailableUntilEpochMs;

        if (chunkRepository != null && !canSkipDeadDb) {
            try {
                vectorHits = chunkRepository.searchByVector(documentIds, queryEmbedding, candidateK);
                textHits = chunkRepository.searchByFullText(documentIds, question, candidateK);
            } catch (Exception e) {
                if (localKnowledgeBaseService != null && localKnowledgeBaseService.hasDocuments()) {
                    dbUnavailableUntilEpochMs = System.currentTimeMillis() + localKnowledgeBaseService.getDbFailureBackoffMs();
                }
                log.debug("Database retrieval unavailable ({}); switching to LocalKnowledgeBaseService", e.getMessage());
            }
        }

        if ((vectorHits == null || vectorHits.isEmpty())
                && (textHits == null || textHits.isEmpty())
                && localKnowledgeBaseService != null) {
            vectorHits = localKnowledgeBaseService.searchByVector(documentIds, queryEmbedding, candidateK);
            textHits = localKnowledgeBaseService.searchByFullText(documentIds, question, candidateK);
            usedLocalKb = true;
        }

        List<RetrievedChunk> safeVectorHits = vectorHits != null ? vectorHits : List.of();
        List<RetrievedChunk> safeTextHits = textHits != null ? textHits : List.of();

        log.info("stage=retrieve source={} vector_candidates={} fulltext_candidates={} candidate_k={}",
                usedLocalKb ? "local_kb" : "postgres",
                safeVectorHits.size(), safeTextHits.size(), candidateK);

        // Fuse bằng RRF.
        List<ReciprocalRankFusion.FusedChunk> fused =
                ReciprocalRankFusion.fuse(List.of(safeVectorHits, safeTextHits), chatProps.getRrfK());

        List<ReciprocalRankFusion.FusedChunk> top;
        if (usedLocalKb && localKnowledgeBaseService != null) {
            top = localKnowledgeBaseService.diversifyTopChunks(fused, question, finalK);
        } else {
            top = fused.size() > finalK ? fused.subList(0, finalK) : fused;
        }

        log.info("stage=rerank fused_total={} returned={} rrf_k={}",
                fused.size(), top.size(), chatProps.getRrfK());
        return top;
    }
}


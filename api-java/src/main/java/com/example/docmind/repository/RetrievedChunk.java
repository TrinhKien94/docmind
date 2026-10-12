package com.example.docmind.repository;

import java.util.List;
import java.util.UUID;

/**
 * 1 chunk lấy ra từ 1 arm retrieval (vector hoặc full-text), kèm điểm số của arm đó.
 * Dùng cho RRF fusion ở tầng service.
 */
public record RetrievedChunk(
        UUID chunkId,
        UUID documentId,
        List<String> headingPath,
        List<UUID> sourceBlockIds,
        int page,
        double[] bbox,
        String text,
        double score
) {
}

package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Citation truy ngược câu trả lời về nguồn gốc trong tài liệu.
 * Khả thi nhờ chunk kế thừa document_id/heading_path/page/bbox/block_ids từ block
 * (canonical-schema §3).
 */
public record Citation(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("document_id") String documentId,
        @JsonProperty("block_ids") List<String> blockIds,
        int page,
        List<Double> bbox,
        @JsonProperty("heading_path") List<String> headingPath,
        String snippet
) {
}

package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Response của GET /api/documents/{id}. */
public record DocumentStatusResponse(
        @JsonProperty("document_id") String documentId,
        String filename,
        String status,
        int pages,
        @JsonProperty("engine_breakdown") Map<String, Integer> engineBreakdown
) {
}

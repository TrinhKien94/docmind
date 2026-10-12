package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Response của POST /api/documents. */
public record DocumentUploadResponse(
        @JsonProperty("document_id") String documentId,
        @JsonProperty("job_id") String jobId,
        String status
) {
}

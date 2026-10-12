package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Response của GET /v1/jobs/{job_id} (doc-service-py).
 * status enum: queued|running|succeeded|failed.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JobStatusResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("document_id") String documentId,
        String status,
        String filename,
        String sha256,
        boolean cached,
        @JsonProperty("pages_total") int pagesTotal,
        @JsonProperty("pages_done") int pagesDone,
        @JsonProperty("page_progress") List<PageProgress> pageProgress,
        @JsonProperty("engine_breakdown") Map<String, Integer> engineBreakdown,
        String error
) {

    public boolean isSucceeded() {
        return "succeeded".equalsIgnoreCase(status);
    }

    public boolean isFailed() {
        return "failed".equalsIgnoreCase(status);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PageProgress(
            int page,
            String kind,
            String status,
            @JsonProperty("block_count") int blockCount
    ) {
    }
}

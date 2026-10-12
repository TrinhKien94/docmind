package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Response của POST /v1/parse (doc-service-py): {job_id, document_id, cached}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ParseSubmitResponse(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("document_id") String documentId,
        boolean cached
) {
}

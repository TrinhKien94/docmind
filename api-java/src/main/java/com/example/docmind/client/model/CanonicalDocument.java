package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Canonical Document — top-level object parser trả về (canonical-schema.md §1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalDocument(
        @JsonProperty("document_id") String documentId,
        CanonicalSource source,
        List<CanonicalBlock> blocks,
        CanonicalStats stats
) {
}

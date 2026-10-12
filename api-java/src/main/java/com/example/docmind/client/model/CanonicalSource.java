package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Canonical Source — provenance của file (canonical-schema.md §1.1). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalSource(
        String filename,
        String sha256,
        String mime,
        int pages
) {
}

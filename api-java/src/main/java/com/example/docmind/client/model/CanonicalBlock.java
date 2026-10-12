package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Canonical Block — khớp VERBATIM docmind/docs/canonical-schema.md §2.
 * Keys snake_case; consumer bỏ qua key lạ (JsonIgnoreProperties).
 *
 * <p>Luật: {@code latex} non-null CHỈ khi {@code type == "formula"}, else null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalBlock(
        @JsonProperty("block_id") String blockId,
        int page,
        int order,
        String type,
        String text,
        String markdown,
        String html,
        String latex,
        List<Double> bbox,
        double confidence,
        @JsonProperty("heading_path") List<String> headingPath,
        @JsonProperty("parse_engine") String parseEngine,
        @JsonProperty("parse_version") String parseVersion
) {
}

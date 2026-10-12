package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/** Canonical Stats — engine_breakdown: {parse_engine -> số block}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalStats(
        @JsonProperty("engine_breakdown") Map<String, Integer> engineBreakdown
) {
}

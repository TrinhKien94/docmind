package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Request body của POST /v1/embed (doc-service-py): {"texts": [...]}. */
public record EmbedRequest(
        @JsonProperty("texts") List<String> texts
) {
}

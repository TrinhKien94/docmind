package com.example.docmind.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response của POST /v1/embed (doc-service-py):
 * {"model": str, "dim": 384, "embeddings": [[...384 floats...], ...]}.
 *
 * <p>Dùng {@code List<List<Float>>} (thay vì {@code List<float[]>}) để Jackson
 * suy generic sạch khi deserialize; RemoteEmbeddingClient map sang {@code float[]}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EmbedResponse(
        @JsonProperty("model") String model,
        @JsonProperty("dim") int dim,
        @JsonProperty("embeddings") List<List<Float>> embeddings
) {
}

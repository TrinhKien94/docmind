package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Cấu hình nguồn nhà cung cấp mô hình (Chat Completion, Embedding, Rerank, TTS, STT)
 * theo thiết kế ProviderPage / ProviderConfigService của AstrBot.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotProviderDto(
        @JsonProperty("id") String id,
        @JsonProperty("provider") String provider,
        @JsonProperty("type") String type,
        @JsonProperty("provider_type") String providerType,
        @JsonProperty("enable") boolean enable,
        @JsonProperty("api_base") String apiBase,
        @JsonProperty("model") String model,
        @JsonProperty("models") List<String> models,
        @JsonProperty("timeout") int timeout,
        @JsonProperty("key_configured") boolean keyConfigured,
        @JsonProperty("masked_key") String maskedKey,
        @JsonProperty("status") String status,
        @JsonProperty("latency_ms") long latencyMs
) {
}

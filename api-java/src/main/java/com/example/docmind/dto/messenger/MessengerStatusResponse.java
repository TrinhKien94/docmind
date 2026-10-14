package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Phản hồi trạng thái tích hợp Facebook Messenger (GET /api/messenger/status).
 */
public record MessengerStatusResponse(
        @JsonProperty("enabled")
        boolean enabled,

        @JsonProperty("verify_token_configured")
        boolean verifyTokenConfigured,

        @JsonProperty("page_token_configured")
        @JsonAlias({"page_access_token_configured"})
        boolean pageTokenConfigured,

        @JsonProperty("app_secret_configured")
        boolean appSecretConfigured,

        @JsonProperty("verify_signature")
        @JsonAlias({"verify_signature_enabled"})
        boolean verifySignature,

        @JsonProperty("active_sessions_count")
        int activeSessionsCount,

        @JsonProperty("webhook_url")
        @JsonAlias({"webhook_path"})
        String webhookUrl,

        @JsonProperty("graph_base_url")
        String graphBaseUrl,

        @JsonProperty("api_version")
        String apiVersion,

        @JsonProperty("recent_interactions")
        List<MessengerInteractionDto> recentInteractions
) {
}

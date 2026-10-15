package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Thông tin cấu hình & trạng thái runtime của một Bot / Platform Adapter đã khởi tạo
 * (tương ứng Platform.get_stats() + BotConfigService.get_bot() trong astrbot).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotInstanceDto(
        @JsonProperty("id") String id,
        @JsonProperty("type") String type,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("enable") boolean enable,
        @JsonProperty("status") String status,
        @JsonProperty("started_at") String startedAt,
        @JsonProperty("unified_webhook") boolean unifiedWebhook,
        @JsonProperty("webhook_uuid") String webhookUuid,
        @JsonProperty("webhook_url") String webhookUrl,
        @JsonProperty("error_count") int errorCount,
        @JsonProperty("last_error") ErrorDetailDto lastError,
        @JsonProperty("message_count") long messageCount,
        @JsonProperty("active_sessions_count") int activeSessionsCount,
        @JsonProperty("fallback_profile_id") String fallbackProfileId,
        @JsonProperty("default_persona_id") String defaultPersonaId,
        @JsonProperty("default_scenario") String defaultScenario,
        @JsonProperty("has_qr_payload") boolean hasQrPayload,
        @JsonProperty("qr_status") String qrStatus,
        @JsonProperty("qrcode_img_content") String qrCodeImgContent,
        @JsonProperty("config") Map<String, Object> config,
        @JsonProperty("routes") List<RouteBindingDto> routes
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetailDto(
            @JsonProperty("message") String message,
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("traceback") String traceback
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RouteBindingDto(
            @JsonProperty("umo") String umo,
            @JsonProperty("config_id") String configId,
            @JsonProperty("persona_id") String personaId,
            @JsonProperty("scenario") String scenario
    ) {
    }
}

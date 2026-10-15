package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Metadata & mẫu cấu hình (config_template) của một loại nền tảng chat trong AstrBot
 * (tương ứng BotConfigService.list_bot_types() trong astrbot).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotPlatformTypeDto(
        @JsonProperty("type") String type,
        @JsonProperty("id") String id,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("category") String category,
        @JsonProperty("description") String description,
        @JsonProperty("icon") String icon,
        @JsonProperty("tutorial_url") String tutorialUrl,
        @JsonProperty("support_streaming_message") boolean supportStreamingMessage,
        @JsonProperty("support_proactive_message") boolean supportProactiveMessage,
        @JsonProperty("support_unified_webhook") boolean supportUnifiedWebhook,
        @JsonProperty("support_qr_login") boolean supportQrLogin,
        @JsonProperty("default_config") Map<String, Object> defaultConfig,
        @JsonProperty("schema") Map<String, AstrBotFieldSchemaDto> schema
) {
}

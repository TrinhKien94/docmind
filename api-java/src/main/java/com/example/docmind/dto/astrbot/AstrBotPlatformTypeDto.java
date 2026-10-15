package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Metadata & mẫu cấu hình (config_template) của một loại nền tảng chat trong AstrBot
 * (tương ứng BotConfigService.list_bot_types() trong astrbot).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @JsonProperty("doc_url")
    public String docUrl() {
        return tutorialUrl;
    }

    @JsonProperty("fields")
    public List<Map<String, Object>> fields() {
        if (schema == null || schema.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> list = new ArrayList<>(schema.size());
        for (AstrBotFieldSchemaDto f : schema.values()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", f.key());
            item.put("label", f.description());
            String uiType = f.secret() ? "password"
                    : ("bool".equalsIgnoreCase(f.type()) || "boolean".equalsIgnoreCase(f.type())) ? "boolean"
                    : ("int".equalsIgnoreCase(f.type()) || "number".equalsIgnoreCase(f.type()) || "float".equalsIgnoreCase(f.type())) ? "number"
                    : "select".equalsIgnoreCase(f.type()) ? "select"
                    : "string";
            item.put("type", uiType);
            item.put("raw_type", f.type());
            item.put("required", f.required());
            item.put("secret", f.secret());
            String placeholder = (f.defaultValue() != null && !String.valueOf(f.defaultValue()).isBlank()
                    && !"true".equalsIgnoreCase(String.valueOf(f.defaultValue()))
                    && !"false".equalsIgnoreCase(String.valueOf(f.defaultValue())))
                    ? String.valueOf(f.defaultValue())
                    : (f.hint() != null ? f.hint() : "");
            item.put("placeholder", placeholder);
            item.put("description", f.hint() != null && !f.hint().isBlank() ? f.hint() : f.description());
            item.put("hint", f.hint());
            if (f.options() != null) {
                item.put("options", f.options());
            }
            if (f.defaultValue() != null) {
                item.put("default_value", f.defaultValue());
            }
            list.add(item);
        }
        return list;
    }
}


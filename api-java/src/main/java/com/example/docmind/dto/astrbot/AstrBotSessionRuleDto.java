package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Thông tin phiên hội thoại hợp nhất UMO (Unified Message Origin: {platform_id}:{message_type}:{session_id})
 * và các quy tắc tuỳ chỉnh (Session Management & Custom Rules) theo thiết kế AstrBot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotSessionRuleDto(
        @JsonProperty("umo") String umo,
        @JsonProperty("platform_id") String platformId,
        @JsonProperty("platform_type") String platformType,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("session_id") String sessionId,
        @JsonProperty("sender_id") String senderId,
        @JsonProperty("sender_name") String senderName,
        @JsonProperty("custom_name") String customName,
        @JsonProperty("llm_enabled") boolean llmEnabled,
        @JsonProperty("tts_enabled") boolean ttsEnabled,
        @JsonProperty("persona_id") String personaId,
        @JsonProperty("scenario") String scenario,
        @JsonProperty("config_profile_id") String configProfileId,
        @JsonProperty("kb_names") List<String> kbNames,
        @JsonProperty("disabled_plugins") List<String> disabledPlugins,
        @JsonProperty("message_count") int messageCount,
        @JsonProperty("last_message") String lastMessage,
        @JsonProperty("last_reply") String lastReply,
        @JsonProperty("last_active_at") String lastActiveAt,
        @JsonProperty("history") List<Map<String, String>> history
) {
}

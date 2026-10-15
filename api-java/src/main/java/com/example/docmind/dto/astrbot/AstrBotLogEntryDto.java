package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Dòng nhật ký hệ thống thời gian thực trong ConsolePage / ConsoleDisplayer của AstrBot.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotLogEntryDto(
        @JsonProperty("id") String id,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("level") String level,
        @JsonProperty("platform_id") String platformId,
        @JsonProperty("source") String source,
        @JsonProperty("message") String message,
        @JsonProperty("is_user_chat") boolean isUserChat
) {
}

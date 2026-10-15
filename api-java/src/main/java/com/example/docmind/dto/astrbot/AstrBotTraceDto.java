package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bản ghi Trace chi tiết của một lượt xử lý sự kiện tin nhắn đa nền tảng (TracePage / TraceDisplayer).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotTraceDto(
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("timestamp") String timestamp,
        @JsonProperty("umo") String umo,
        @JsonProperty("bot_id") String botId,
        @JsonProperty("platform_type") String platformType,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("sender_id") String senderId,
        @JsonProperty("sender_name") String senderName,
        @JsonProperty("incoming_message") String incomingMessage,
        @JsonProperty("reply_message") String replyMessage,
        @JsonProperty("resolved_scenario") String resolvedScenario,
        @JsonProperty("resolved_persona_id") String resolvedPersonaId,
        @JsonProperty("citations_count") int citationsCount,
        @JsonProperty("evo_score") double evoScore,
        @JsonProperty("evo_gen") int evoGen,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("is_command") boolean isCommand,
        @JsonProperty("status") String status
) {
}

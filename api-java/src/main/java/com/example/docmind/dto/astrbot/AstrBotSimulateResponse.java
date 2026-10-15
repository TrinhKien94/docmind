package com.example.docmind.dto.astrbot;

import com.example.docmind.dto.Citation;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Kết quả trả về sau khi xử lý tin nhắn đa nền tảng qua AstrBot Event Pipeline + RAG + EvoAgentX.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotSimulateResponse(
        @JsonProperty("interaction_id") String interactionId,
        @JsonProperty("umo") String umo,
        @JsonProperty("bot_id") String botId,
        @JsonProperty("platform_type") String platformType,
        @JsonProperty("message_type") String messageType,
        @JsonProperty("sender_id") String senderId,
        @JsonProperty("sender_name") String senderName,
        @JsonProperty("received_message") String receivedMessage,
        @JsonProperty("reply_sent") String replySent,
        @JsonProperty("resolved_scenario") String resolvedScenario,
        @JsonProperty("resolved_persona_id") String resolvedPersonaId,
        @JsonProperty("resolved_profile_id") String resolvedProfileId,
        @JsonProperty("is_command") boolean isCommand,
        @JsonProperty("citations") List<Citation> citations,
        @JsonProperty("quick_replies") List<String> quickReplies,
        @JsonProperty("evo_generation") int evoGeneration,
        @JsonProperty("evo_score") double evoScore,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("outbound_dispatched") boolean outboundDispatched,
        @JsonProperty("timestamp") String timestamp
) {
}

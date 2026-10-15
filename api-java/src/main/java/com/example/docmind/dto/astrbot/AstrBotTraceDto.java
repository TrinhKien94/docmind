package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Bản ghi Trace chi tiết của một lượt xử lý sự kiện tin nhắn đa nền tảng (TracePage / TraceDisplayer).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        @JsonProperty("status") String status,
        @JsonProperty("stages") List<String> stages
) {
    public static final List<String> DEFAULT_PIPELINE_STAGES = List.of(
            "WakingStage",
            "WhitelistCheckStage",
            "SessionStatusCheckStage",
            "RateLimitStage",
            "ProcessStage",
            "RespondStage"
    );

    public AstrBotTraceDto {
        if (stages == null || stages.isEmpty()) {
            stages = DEFAULT_PIPELINE_STAGES;
        }
    }

    public AstrBotTraceDto(
            String traceId,
            String timestamp,
            String umo,
            String botId,
            String platformType,
            String messageType,
            String senderId,
            String senderName,
            String incomingMessage,
            String replyMessage,
            String resolvedScenario,
            String resolvedPersonaId,
            int citationsCount,
            double evoScore,
            int evoGen,
            long latencyMs,
            boolean isCommand,
            String status) {
        this(
                traceId, timestamp, umo, botId, platformType, messageType,
                senderId, senderName, incomingMessage, replyMessage,
                resolvedScenario, resolvedPersonaId, citationsCount,
                evoScore, evoGen, latencyMs, isCommand, status,
                DEFAULT_PIPELINE_STAGES
        );
    }

    @JsonProperty("platform_id")
    public String platformId() {
        return botId;
    }

    @JsonProperty("question")
    public String question() {
        return incomingMessage;
    }

    @JsonProperty("reply_excerpt")
    public String replyExcerpt() {
        return replyMessage;
    }

    @JsonProperty("scenario")
    public String scenario() {
        return resolvedScenario;
    }

    @JsonProperty("persona_id")
    public String personaId() {
        return resolvedPersonaId;
    }
}


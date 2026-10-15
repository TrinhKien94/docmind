package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu giả lập hoặc gửi sự kiện hội thoại đa nền tảng qua AstrBot Event Pipeline.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AstrBotSimulateRequest {

    @JsonProperty("bot_id")
    private String botId;

    @JsonProperty("platform_type")
    private String platformType;

    @JsonProperty("message_type")
    private String messageType;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("sender_id")
    private String senderId;

    @JsonProperty("sender_name")
    private String senderName;

    @JsonProperty("message")
    private String message;

    @JsonProperty("scenario")
    private String scenario;

    @JsonProperty("persona_id")
    private String personaId;
}

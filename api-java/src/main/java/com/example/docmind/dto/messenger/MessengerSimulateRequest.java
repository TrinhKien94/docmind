package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request giả lập gửi tin nhắn khách hàng tới Facebook Messenger bot (POST /api/messenger/simulate).
 */
public record MessengerSimulateRequest(
        @JsonProperty("sender_id")
        @JsonAlias({"senderId"})
        String senderId,

        @NotBlank(message = "message không được để trống")
        @JsonProperty("message")
        @JsonAlias({"text", "question", "user_message", "userMessage"})
        String message,

        @JsonProperty("scenario")
        String scenario,

        @JsonProperty("postback_payload")
        @JsonAlias({"postbackPayload"})
        String postbackPayload
) {
    public MessengerSimulateRequest(String senderId, String message, String scenario) {
        this(senderId, message, scenario, null);
    }
}

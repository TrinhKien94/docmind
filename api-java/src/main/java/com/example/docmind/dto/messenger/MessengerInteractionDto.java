package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bản ghi tóm tắt một lượt tương tác giữa khách hàng Messenger và DocMind bot.
 */
public record MessengerInteractionDto(
        @JsonProperty("interaction_id")
        String interactionId,

        @JsonProperty("sender_id")
        String senderId,

        @JsonProperty("incoming_text")
        @JsonAlias({"question", "user_message", "incomingText"})
        String incomingText,

        @JsonProperty("reply_text")
        @JsonAlias({"answer", "bot_response", "replyText"})
        String replyText,

        @JsonProperty("scenario")
        String scenario,

        @JsonProperty("delivery_status")
        @JsonAlias({"deliveryStatus"})
        String deliveryStatus,

        @JsonProperty("quick_replies_count")
        @JsonAlias({"quickRepliesCount"})
        int quickRepliesCount,

        @JsonProperty("timestamp")
        String timestamp,

        @JsonProperty("simulated")
        boolean simulated
) {
    public MessengerInteractionDto(
            String interactionId,
            String senderId,
            String incomingText,
            String replyText,
            String scenario,
            String deliveryStatus,
            String timestamp) {
        this(interactionId, senderId, incomingText, replyText, scenario, deliveryStatus, 0,
                timestamp, "SIMULATED".equalsIgnoreCase(deliveryStatus));
    }

    public MessengerInteractionDto(
            String interactionId,
            String senderId,
            String incomingText,
            String replyText,
            String scenario,
            int quickRepliesCount,
            String timestamp,
            boolean simulated) {
        this(interactionId, senderId, incomingText, replyText, scenario,
                simulated ? "SIMULATED" : "SENT", quickRepliesCount, timestamp, simulated);
    }
}

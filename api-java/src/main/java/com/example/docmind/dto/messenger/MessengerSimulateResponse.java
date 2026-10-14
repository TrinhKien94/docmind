package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Phản hồi giả lập cuộc trò chuyện Messenger (POST /api/messenger/simulate).
 */
public record MessengerSimulateResponse(
        @JsonProperty("sender_id")
        String senderId,

        @JsonProperty("received_message")
        @JsonAlias({"user_message", "receivedMessage"})
        String receivedMessage,

        @JsonProperty("scenario")
        String scenario,

        @JsonProperty("reply_sent")
        @JsonAlias({"reply_text", "replySent"})
        String replySent,

        @JsonProperty("quick_replies")
        @JsonAlias({"quickReplies"})
        List<String> quickReplies,

        @JsonProperty("interaction_id")
        String interactionId,

        @JsonProperty("simulated")
        boolean simulated,

        @JsonProperty("timestamp")
        String timestamp
) {
}

package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Payload gửi phản hồi qua Meta Graph Send API (POST /me/messages).
 */
public record MessengerSendRequest(
        @JsonProperty("recipient") Recipient recipient,
        @JsonProperty("messaging_type") String messagingType,
        @JsonProperty("message") OutgoingMessage message
) {
    public record Recipient(
            @JsonProperty("id") String id
    ) {}

    public record OutgoingMessage(
            @JsonProperty("text") String text,
            @JsonProperty("quick_replies") List<QuickReplyItem> quickReplies
    ) {}

    public record QuickReplyItem(
            @JsonProperty("content_type") String contentType,
            @JsonProperty("title") String title,
            @JsonProperty("payload") String payload
    ) {
        public static QuickReplyItem text(String title, String payload) {
            return new QuickReplyItem("text", title, payload);
        }
    }
}

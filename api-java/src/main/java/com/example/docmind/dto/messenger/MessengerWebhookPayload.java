package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Root JSON payload gửi từ Meta Platform tới POST /api/messenger/webhook.
 */
public record MessengerWebhookPayload(
        @JsonProperty("object") String object,
        @JsonProperty("entry") List<MessengerEntry> entry
) {
    // Nested aliases cho tương thích với code tham chiếu inner record
    public record WebhookEntry(
            String id,
            Long time,
            List<MessengerMessaging> messaging
    ) {}

    public record MessagingEvent(
            MessengerSender sender,
            MessengerRecipient recipient,
            Long timestamp,
            MessengerMessage message,
            MessengerPostback postback,
            MessengerReaction reaction
    ) {}
}

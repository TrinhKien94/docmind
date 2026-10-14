package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Một sự kiện messaging trong danh sách entry[].messaging[] của Meta webhook payload.
 */
public record MessengerMessaging(
        @JsonProperty("sender") MessengerSender sender,
        @JsonProperty("recipient") MessengerRecipient recipient,
        @JsonProperty("timestamp") Long timestamp,
        @JsonProperty("message") MessengerMessage message,
        @JsonProperty("postback") MessengerPostback postback,
        @JsonProperty("reaction") MessengerReaction reaction,
        @JsonProperty("delivery") MessengerDelivery delivery,
        @JsonProperty("read") MessengerRead read
) {
    public MessengerMessaging(MessengerSender sender, MessengerRecipient recipient, Long timestamp,
                              MessengerMessage message, MessengerPostback postback, MessengerReaction reaction) {
        this(sender, recipient, timestamp, message, postback, reaction, null, null);
    }
}

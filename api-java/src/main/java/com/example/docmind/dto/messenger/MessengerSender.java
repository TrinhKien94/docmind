package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Định danh người gửi tin nhắn (PSID) trong webhook payload của Facebook Messenger.
 */
public record MessengerSender(
        @JsonProperty("id") String id
) {
}

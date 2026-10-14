package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Định danh người nhận tin nhắn (Page ID hoặc PSID) trong Facebook Messenger.
 */
public record MessengerRecipient(
        @JsonProperty("id") String id
) {
}

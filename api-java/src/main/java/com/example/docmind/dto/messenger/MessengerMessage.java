package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Nội dung tin nhắn đến từ người dùng trong sự kiện webhook Messenger.
 */
public record MessengerMessage(
        @JsonProperty("mid") String mid,
        @JsonProperty("text") String text,
        @JsonProperty("quick_reply") @JsonAlias({"quickReply"}) MessengerQuickReply quickReply,
        @JsonProperty("is_echo") @JsonAlias({"isEcho"}) Boolean isEcho
) {
}

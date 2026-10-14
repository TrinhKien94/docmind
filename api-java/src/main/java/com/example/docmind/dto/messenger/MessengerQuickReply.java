package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Đối tượng quick reply đi kèm trong tin nhắn đến khi người dùng bấm chọn một nút gợi ý.
 */
public record MessengerQuickReply(
        @JsonProperty("payload") String payload
) {
}

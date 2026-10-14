package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sự kiện xác nhận người dùng đã xem tin nhắn từ Meta Messenger.
 */
public record MessengerRead(
        @JsonProperty("watermark") Long watermark,
        @JsonProperty("seq") Integer seq
) {
}

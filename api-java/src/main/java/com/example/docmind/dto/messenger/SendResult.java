package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Kết quả gửi tin nhắn qua Facebook Graph Send API (hoặc chế độ SIMULATED).
 */
public record SendResult(
        @JsonProperty("success") boolean success,
        @JsonProperty("message_id") String messageId,
        @JsonProperty("status") String status
) {
}

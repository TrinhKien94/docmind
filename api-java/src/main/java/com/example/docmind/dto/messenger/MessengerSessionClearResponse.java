package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Phản hồi sau khi xoá/reset các phiên Messenger (DELETE /api/messenger/sessions).
 */
public record MessengerSessionClearResponse(
        @JsonProperty("message")
        @JsonAlias({"status"})
        String message,

        @JsonProperty("cleared_count")
        @JsonAlias({"cleared_sessions", "clearedCount"})
        int clearedCount,

        @JsonProperty("timestamp")
        String timestamp
) {
}

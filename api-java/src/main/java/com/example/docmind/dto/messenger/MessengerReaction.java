package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sự kiện reaction trên tin nhắn Messenger (like, love, angry, sad, wow, haha).
 */
public record MessengerReaction(
        @JsonProperty("reaction") String reaction,
        @JsonProperty("emoji") String emoji,
        @JsonProperty("action") String action,
        @JsonProperty("mid") String mid
) {
}

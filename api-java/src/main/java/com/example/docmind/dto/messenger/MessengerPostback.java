package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sự kiện postback khi người dùng bấm vào một nút CTA hoặc card trong Messenger.
 */
public record MessengerPostback(
        @JsonProperty("mid") String mid,
        @JsonProperty("title") String title,
        @JsonProperty("payload") String payload
) {
    public MessengerPostback(String title, String payload) {
        this(null, title, payload);
    }
}

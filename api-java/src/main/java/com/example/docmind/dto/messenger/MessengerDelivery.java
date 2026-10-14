package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Sự kiện xác nhận đã chuyển phát tin nhắn từ Meta Messenger.
 */
public record MessengerDelivery(
        @JsonProperty("mids") List<String> mids,
        @JsonProperty("watermark") Long watermark,
        @JsonProperty("seq") Integer seq
) {
}

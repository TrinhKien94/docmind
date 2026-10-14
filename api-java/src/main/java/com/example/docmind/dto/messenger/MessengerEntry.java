package com.example.docmind.dto.messenger;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Một entry trong payload webhook của Facebook Page.
 */
public record MessengerEntry(
        @JsonProperty("id") String id,
        @JsonProperty("time") Long time,
        @JsonProperty("messaging") List<MessengerMessaging> messaging
) {
}

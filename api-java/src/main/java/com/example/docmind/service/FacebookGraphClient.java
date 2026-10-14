package com.example.docmind.service;

import com.example.docmind.config.MessengerProperties;
import com.example.docmind.dto.messenger.MessengerSendRequest;
import com.example.docmind.dto.messenger.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP Client giao tiếp với Meta Graph Send API (POST /me/messages).
 * Tự động chuyển sang chế độ SIMULATED khi chưa cấu hình pageAccessToken (an toàn khi chạy test/dev).
 */
@Component
public class FacebookGraphClient {

    private static final Logger log = LoggerFactory.getLogger(FacebookGraphClient.class);

    private final MessengerProperties properties;
    private final RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    public FacebookGraphClient(MessengerProperties properties) {
        this(properties, null);
    }

    public FacebookGraphClient(MessengerProperties properties, RestClient restClient) {
        this.properties = properties != null ? properties : new MessengerProperties();
        if (restClient != null) {
            this.restClient = restClient;
        } else {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofMillis(
                    this.properties.getConnectTimeoutMs() > 0 ? this.properties.getConnectTimeoutMs() : 5000));
            requestFactory.setReadTimeout(Duration.ofMillis(
                    this.properties.getReadTimeoutMs() > 0 ? this.properties.getReadTimeoutMs() : 10000));
            this.restClient = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build();
        }
    }

    public SendResult sendTextMessage(String recipientId, String text, List<MessengerSendRequest.QuickReplyItem> quickReplies) {
        if (recipientId == null || recipientId.isBlank()) {
            return new SendResult(false, null, "FAILED: recipientId is blank");
        }

        if (properties.getPageAccessToken() == null || properties.getPageAccessToken().isBlank()) {
            log.info("Page access token is blank. Operating in SIMULATED mode for recipient={}", recipientId);
            return new SendResult(true, "simulated-mid-" + UUID.randomUUID(), "SIMULATED");
        }

        try {
            String baseUrl = properties.getGraphBaseUrl() != null && !properties.getGraphBaseUrl().isBlank()
                    ? properties.getGraphBaseUrl().strip()
                    : "https://graph.facebook.com";
            if (baseUrl.endsWith("/")) {
                baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
            }
            String apiVersion = properties.getApiVersion() != null && !properties.getApiVersion().isBlank()
                    ? properties.getApiVersion().strip()
                    : "v21.0";
            if (apiVersion.startsWith("/")) {
                apiVersion = apiVersion.substring(1);
            }
            String sendPath = properties.getSendPath() != null && !properties.getSendPath().isBlank()
                    ? properties.getSendPath().strip()
                    : "/me/messages";
            if (!sendPath.startsWith("/")) {
                sendPath = "/" + sendPath;
            }

            String url = String.format("%s/%s%s", baseUrl, apiVersion, sendPath);

            MessengerSendRequest request = new MessengerSendRequest(
                    new MessengerSendRequest.Recipient(recipientId),
                    "RESPONSE",
                    new MessengerSendRequest.OutgoingMessage(text, quickReplies)
            );

            Map<?, ?> response = restClient.post()
                    .uri(url)
                    .header("Authorization", "Bearer " + properties.getPageAccessToken().strip())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(Map.class);

            String mid = response != null && response.containsKey("message_id")
                    ? String.valueOf(response.get("message_id"))
                    : "mid-" + UUID.randomUUID();

            log.info("Successfully dispatched message to Facebook Graph API for recipient={}, mid={}", recipientId, mid);
            return new SendResult(true, mid, "SENT");
        } catch (RestClientResponseException ex) {
            log.error("Meta Graph API returned error (status={}): {}",
                    ex.getStatusCode(), ex.getResponseBodyAsString());
            return new SendResult(false, null, "FAILED: " + ex.getStatusCode());
        } catch (Exception ex) {
            log.error("Failed to dispatch message to Facebook Graph API: {}", ex.getMessage());
            return new SendResult(false, null, "ERROR: " + ex.getMessage());
        }
    }
}

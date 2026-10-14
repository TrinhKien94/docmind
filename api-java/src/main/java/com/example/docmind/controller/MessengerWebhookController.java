package com.example.docmind.controller;

import com.example.docmind.config.MessengerProperties;
import com.example.docmind.dto.messenger.MessengerSessionClearResponse;
import com.example.docmind.dto.messenger.MessengerSimulateRequest;
import com.example.docmind.dto.messenger.MessengerSimulateResponse;
import com.example.docmind.dto.messenger.MessengerStatusResponse;
import com.example.docmind.service.MessengerService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller tiếp nhận Webhook từ Facebook Messenger và cung cấp các API quản lý / giả lập.
 */
@RestController
public class MessengerWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MessengerWebhookController.class);

    private final MessengerService messengerService;
    private final MessengerProperties properties;

    @org.springframework.beans.factory.annotation.Autowired
    public MessengerWebhookController(MessengerService messengerService, MessengerProperties properties) {
        this.messengerService = messengerService;
        this.properties = properties;
    }

    /**
     * Xác minh Webhook subscription challenge theo chuẩn Meta Platform (GET).
     */
    @GetMapping(value = "/api/messenger/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verifyWebhook(
            @RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        if (mode == null || mode.isBlank() || verifyToken == null || verifyToken.isBlank() || challenge == null || challenge.isBlank()) {
            log.warn("Missing required hub parameters for webhook verification: mode={}, token={}, challenge={}",
                    mode, verifyToken != null ? "[PRESENT]" : "[NULL]", challenge != null ? "[PRESENT]" : "[NULL]");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Bad Request: Missing required hub parameters");
        }
        try {
            String result = messengerService.verifyWebhook(mode, verifyToken, challenge);
            return ResponseEntity.ok(result);
        } catch (SecurityException se) {
            log.warn("Webhook verification failed: {}", se.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Token verification failed");
        }
    }

    /**
     * Tiếp nhận các sự kiện webhook từ Meta Platform (POST).
     */
    @PostMapping(value = "/api/messenger/webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> receiveWebhookEvent(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signatureHeader,
            @RequestBody String rawPayload) {
        try {
            messengerService.handleWebhookPayload(signatureHeader, rawPayload);
            return ResponseEntity.ok("EVENT_RECEIVED");
        } catch (SecurityException se) {
            log.warn("Webhook signature check rejected request: {}", se.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Forbidden: Invalid signature");
        }
    }

    /**
     * Lấy trạng thái cấu hình và lịch sử tương tác gần nhất của tích hợp Messenger (GET).
     */
    @GetMapping("/api/messenger/status")
    public ResponseEntity<MessengerStatusResponse> getStatus() {
        return ResponseEntity.ok(messengerService.getStatus());
    }

    /**
     * Giả lập gửi tin nhắn khách hàng tới Facebook Messenger bot (POST).
     */
    @PostMapping("/api/messenger/simulate")
    public ResponseEntity<MessengerSimulateResponse> simulateMessage(
            @Valid @RequestBody MessengerSimulateRequest request) {
        return ResponseEntity.ok(messengerService.simulateMessage(request));
    }

    /**
     * Xoá/reset phiên hội thoại đa lượt của khách hàng (DELETE).
     */
    @DeleteMapping("/api/messenger/sessions")
    public ResponseEntity<MessengerSessionClearResponse> clearSessions(
            @RequestParam(value = "sender_id", required = false) String senderId) {
        return ResponseEntity.ok(messengerService.clearSessions(senderId));
    }
}

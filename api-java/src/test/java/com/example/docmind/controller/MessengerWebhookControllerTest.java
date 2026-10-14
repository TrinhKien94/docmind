package com.example.docmind.controller;

import com.example.docmind.config.MessengerProperties;
import com.example.docmind.dto.messenger.MessengerInteractionDto;
import com.example.docmind.dto.messenger.MessengerSessionClearResponse;
import com.example.docmind.dto.messenger.MessengerSimulateRequest;
import com.example.docmind.dto.messenger.MessengerSimulateResponse;
import com.example.docmind.dto.messenger.MessengerStatusResponse;
import com.example.docmind.service.MessengerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MessengerWebhookController.class)
class MessengerWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessengerService messengerService;

    @MockitoBean
    private MessengerProperties messengerProperties;

    // --- 1. Webhook Verification Tests (GET /api/messenger/webhook) ---

    @Test
    @DisplayName("GET webhook với hub.mode=subscribe và token đúng -> trả về 200 kèm hub.challenge dạng plain text")
    void verifyWebhookSuccessReturnsChallenge() throws Exception {
        when(messengerService.verifyWebhook("subscribe", "my-secret-token", "challenge_12345"))
                .thenReturn("challenge_12345");

        mockMvc.perform(get("/api/messenger/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "my-secret-token")
                        .param("hub.challenge", "challenge_12345"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("challenge_12345"));
    }

    @Test
    @DisplayName("GET webhook với verify token sai -> trả về 403 Forbidden")
    void verifyWebhookInvalidTokenReturnsForbidden() throws Exception {
        when(messengerService.verifyWebhook("subscribe", "wrong-token", "challenge_12345"))
                .thenThrow(new SecurityException("Forbidden: Token verification failed"));

        mockMvc.perform(get("/api/messenger/webhook")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong-token")
                        .param("hub.challenge", "challenge_12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET webhook với hub.mode khác subscribe -> trả về 403 Forbidden")
    void verifyWebhookInvalidModeReturnsForbidden() throws Exception {
        when(messengerService.verifyWebhook("publish", "my-secret-token", "challenge_12345"))
                .thenThrow(new SecurityException("Forbidden: Invalid mode"));

        mockMvc.perform(get("/api/messenger/webhook")
                        .param("hub.mode", "publish")
                        .param("hub.verify_token", "my-secret-token")
                        .param("hub.challenge", "challenge_12345"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET webhook thiếu tham số bắt buộc -> trả về 400 Bad Request hoặc 4xx")
    void verifyWebhookMissingParamsReturnsError() throws Exception {
        mockMvc.perform(get("/api/messenger/webhook")
                        .param("hub.mode", "subscribe"))
                .andExpect(status().is4xxClientError());
    }

    // --- 2. Webhook Event Receiver Tests (POST /api/messenger/webhook) ---

    @Test
    @DisplayName("POST webhook nhận payload hợp lệ -> trả về 200 EVENT_RECEIVED và gọi service")
    void webhookReceiverAcceptsValidMessageEvent() throws Exception {
        String payloadJson = """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "1001",
                      "time": 1728882000000,
                      "messaging": [
                        {
                          "sender": { "id": "psid-001" },
                          "recipient": { "id": "page-001" },
                          "timestamp": 1728882000000,
                          "message": { "mid": "m-001", "text": "Tư vấn gói Pro giúp mình" }
                        }
                      ]
                    }
                  ]
                }
                """;

        when(messengerService.handleWebhookPayload(any(), eq(payloadJson))).thenReturn(true);

        mockMvc.perform(post("/api/messenger/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        verify(messengerService).handleWebhookPayload(any(), eq(payloadJson));
    }

    @Test
    @DisplayName("POST webhook khi chữ ký HMAC-SHA256 sai -> trả về 403 Forbidden")
    void webhookReceiverRejectsInvalidSignature() throws Exception {
        String payloadJson = "{\"object\":\"page\",\"entry\":[]}";

        when(messengerService.handleWebhookPayload(eq("sha256=invalid_hash"), eq(payloadJson)))
                .thenThrow(new SecurityException("Invalid HMAC-SHA256 signature"));

        mockMvc.perform(post("/api/messenger/webhook")
                        .header("X-Hub-Signature-256", "sha256=invalid_hash")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payloadJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST webhook với postback payload FEEDBACK_HELPFUL -> trả về 200 EVENT_RECEIVED")
    void webhookReceiverAcceptsPostbackFeedback() throws Exception {
        String postbackJson = """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "1001",
                      "time": 1728882000000,
                      "messaging": [
                        {
                          "sender": { "id": "psid-001" },
                          "recipient": { "id": "page-001" },
                          "timestamp": 1728882000000,
                          "postback": {
                            "title": "👍 Hài lòng",
                            "payload": "FEEDBACK_HELPFUL_ix-sales-001"
                          }
                        }
                      ]
                    }
                  ]
                }
                """;

        when(messengerService.handleWebhookPayload(any(), eq(postbackJson))).thenReturn(true);

        mockMvc.perform(post("/api/messenger/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postbackJson))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));
    }

    // --- 3. Status Endpoint Tests (GET /api/messenger/status) ---

    @Test
    @DisplayName("GET /api/messenger/status -> trả về 200 OK với đầy đủ thông tin cấu hình và thống kê")
    void getStatusReturnsIntegrationInfo() throws Exception {
        MessengerInteractionDto interaction = new MessengerInteractionDto(
                "ix-001", "psid-001", "Tư vấn gói Pro", "Dạ gói Pro có giá 499k/tháng [1].",
                "SALES", "SIMULATED", "2026-10-14T04:40:00Z");

        MessengerStatusResponse mockStatus = new MessengerStatusResponse(
                true, true, false, true, false, 1,
                "/api/messenger/webhook", "https://graph.facebook.com", "v21.0",
                List.of(interaction)
        );
        when(messengerService.getStatus()).thenReturn(mockStatus);

        mockMvc.perform(get("/api/messenger/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.verify_token_configured").value(true))
                .andExpect(jsonPath("$.page_token_configured").value(false))
                .andExpect(jsonPath("$.app_secret_configured").value(true))
                .andExpect(jsonPath("$.active_sessions_count").value(1))
                .andExpect(jsonPath("$.webhook_url").value("/api/messenger/webhook"))
                .andExpect(jsonPath("$.recent_interactions[0].sender_id").value("psid-001"));
    }

    // --- 4. Simulator Endpoint Tests (POST /api/messenger/simulate) ---

    @Test
    @DisplayName("POST /api/messenger/simulate -> trả về 200 OK với kết quả chat giả lập end-to-end")
    void simulateChatReturnsSuccessfulResponse() throws Exception {
        MessengerSimulateResponse mockResponse = new MessengerSimulateResponse(
                "sim-001", "Tư vấn gói Pro", "SALES",
                "Dạ em chào anh! Gói Pro hiện có mức phí 499.000đ/tháng [1].",
                List.of("Báo giá gói năm", "👍 Hài lòng", "👎 Cần cải thiện"),
                "ix-sim-001", true, "2026-10-14T04:42:00Z"
        );
        when(messengerService.simulateMessage(any(MessengerSimulateRequest.class))).thenReturn(mockResponse);

        String requestJson = """
                {
                  "sender_id": "sim-001",
                  "message": "Tư vấn gói Pro",
                  "scenario": "SALES"
                }
                """;

        mockMvc.perform(post("/api/messenger/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sender_id").value("sim-001"))
                .andExpect(jsonPath("$.reply_sent").value("Dạ em chào anh! Gói Pro hiện có mức phí 499.000đ/tháng [1]."))
                .andExpect(jsonPath("$.scenario").value("SALES"))
                .andExpect(jsonPath("$.simulated").value(true))
                .andExpect(jsonPath("$.quick_replies").isArray())
                .andExpect(jsonPath("$.quick_replies.length()").value(3));
    }

    @Test
    @DisplayName("POST /api/messenger/simulate với message rỗng -> trả về 400 Bad Request")
    void simulateChatRejectsBlankMessage() throws Exception {
        String invalidJson = """
                {
                  "sender_id": "sim-001",
                  "message": "   "
                }
                """;

        mockMvc.perform(post("/api/messenger/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    // --- 5. Session Management Tests (DELETE /api/messenger/sessions) ---

    @Test
    @DisplayName("DELETE /api/messenger/sessions -> xoá toàn bộ phiên và trả về 200 OK")
    void clearSessionsReturnsClearedCount() throws Exception {
        MessengerSessionClearResponse clearResp = new MessengerSessionClearResponse(
                "Messenger sessions cleared successfully", 3, "2026-10-14T04:45:00Z");
        when(messengerService.clearSessions(null)).thenReturn(clearResp);

        mockMvc.perform(delete("/api/messenger/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared_count").value(3))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("DELETE /api/messenger/sessions?sender_id=psid-001 -> xoá phiên cụ thể")
    void clearSpecificSessionReturnsOneCleared() throws Exception {
        MessengerSessionClearResponse clearResp = new MessengerSessionClearResponse(
                "Session for psid-001 cleared", 1, "2026-10-14T04:45:00Z");
        when(messengerService.clearSessions("psid-001")).thenReturn(clearResp);

        mockMvc.perform(delete("/api/messenger/sessions")
                        .param("sender_id", "psid-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared_count").value(1));
    }
}

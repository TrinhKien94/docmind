package com.example.docmind.service;

import com.example.docmind.config.MessengerProperties;
import com.example.docmind.dto.Citation;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.dto.messenger.MessengerSendRequest;
import com.example.docmind.dto.messenger.MessengerSimulateRequest;
import com.example.docmind.dto.messenger.MessengerSimulateResponse;
import com.example.docmind.dto.messenger.MessengerStatusResponse;
import com.example.docmind.dto.messenger.SendResult;
import com.example.docmind.service.evolution.CustomerSelfEvolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessengerServiceTest {

    private CustomerChatService customerChatService;
    private CustomerSelfEvolutionService evolutionService;
    private FacebookGraphClient graphClient;
    private MessengerProperties properties;
    private MessengerService messengerService;

    @BeforeEach
    void setUp() {
        customerChatService = mock(CustomerChatService.class);
        evolutionService = mock(CustomerSelfEvolutionService.class);
        graphClient = mock(FacebookGraphClient.class);
        properties = new MessengerProperties();
        properties.setEnabled(true);
        properties.setVerifyToken("test-verify-token");
        properties.setAppSecret("test-app-secret-1234567890abcdef");
        properties.setVerifySignature(false);
        properties.setPageAccessToken("test-page-access-token");
        properties.setMaxQuickReplies(5);
        properties.setMaxQuickReplyTitleChars(20);
        properties.setSessionMaxTurns(5);
        properties.setMaxSessions(10);
        properties.setSendFeedbackQuickReplies(true);

        messengerService = new MessengerService(
                properties, customerChatService, evolutionService, graphClient);
    }

    // --- 1. Webhook Verification ---

    @Test
    @DisplayName("verifyWebhook hợp lệ -> trả về challenge")
    void verifyWebhookValidReturnsChallenge() {
        String result = messengerService.verifyWebhook("subscribe", "test-verify-token", "challenge_999");
        assertThat(result).isEqualTo("challenge_999");
    }

    @Test
    @DisplayName("verifyWebhook sai token hoặc mode -> ném SecurityException")
    void verifyWebhookInvalidThrowsSecurityException() {
        assertThatThrownBy(() -> messengerService.verifyWebhook("subscribe", "wrong-token", "ch"))
                .isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> messengerService.verifyWebhook("other", "test-verify-token", "ch"))
                .isInstanceOf(SecurityException.class);
    }

    // --- 2. HMAC-SHA256 Signature Verification ---

    @Test
    @DisplayName("Xác minh chữ ký HMAC-SHA256 đúng -> true; sai secret hoặc body bị sửa -> false")
    void hmacSha256SignatureVerification() throws Exception {
        properties.setVerifySignature(true);
        String body = "{\"object\":\"page\",\"entry\":[]}";
        String validSignature = "sha256=" + computeHmacSha256(body, properties.getAppSecret());

        assertThat(messengerService.verifySignature(validSignature, body)).isTrue();
        assertThat(messengerService.verifySignature("sha256=invalid_hash", body)).isFalse();
        assertThat(messengerService.verifySignature("invalid_no_prefix", body)).isFalse();
        assertThat(messengerService.verifySignature(null, body)).isFalse();

        // Thay đổi 1 ký tự trong body -> xác minh thất bại
        String tamperedBody = "{\"object\":\"page\",\"entry\":[ ]}";
        assertThat(messengerService.verifySignature(validSignature, tamperedBody)).isFalse();
    }

    @Test
    @DisplayName("Khi verifySignature=false hoặc appSecret rỗng -> bỏ qua xác thực chữ ký")
    void signatureVerificationBypassedWhenDisabled() {
        properties.setVerifySignature(false);
        assertThat(messengerService.verifySignature("sha256=anything", "some body")).isTrue();

        properties.setVerifySignature(true);
        properties.setAppSecret("");
        assertThat(messengerService.verifySignature("sha256=anything", "some body")).isTrue();
    }

    @Test
    @DisplayName("Khi verifySignature=true: thiếu header X-Hub-Signature-256 -> ném SecurityException")
    void handleWebhookPayloadRejectsMissingSignatureWhenVerifySignatureEnabled() {
        properties.setVerifySignature(true);
        String payloadJson = createMessageWebhookJson("psid-sec-1", "Hello");

        assertThatThrownBy(() -> messengerService.handleWebhookPayload(null, payloadJson))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("Khi verifySignature=true: header X-Hub-Signature-256 rỗng hoặc blank -> ném SecurityException")
    void handleWebhookPayloadRejectsBlankSignatureWhenVerifySignatureEnabled() {
        properties.setVerifySignature(true);
        String payloadJson = createMessageWebhookJson("psid-sec-2", "Hello");

        assertThatThrownBy(() -> messengerService.handleWebhookPayload("   ", payloadJson))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("signature");

        assertThatThrownBy(() -> messengerService.handleWebhookPayload("", payloadJson))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("Khi verifySignature=true: chữ ký sai hoặc bị can thiệp -> ném SecurityException")
    void handleWebhookPayloadRejectsInvalidSignatureWhenVerifySignatureEnabled() {
        properties.setVerifySignature(true);
        String payloadJson = createMessageWebhookJson("psid-sec-3", "Hello");

        assertThatThrownBy(() -> messengerService.handleWebhookPayload("sha256=bad_hex", payloadJson))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("signature");
    }

    @Test
    @DisplayName("Khi verifySignature=true: chữ ký HMAC-SHA256 hợp lệ -> xác thực thành công và xử lý sự kiện")
    void handleWebhookPayloadAcceptsValidHmacSignatureWhenVerifySignatureEnabled() throws Exception {
        properties.setVerifySignature(true);
        CustomerChatResponse chatResponse = new CustomerChatResponse(
                "Chào bạn", "Khách hàng", "GENERAL", "t1", "m1", List.of(), List.of(), "ix-sec-01", 1, 0);
        when(customerChatService.chat(any())).thenReturn(chatResponse);
        when(graphClient.sendTextMessage(any(), any(), anyList())).thenReturn(new SendResult(true, "mid", "SENT"));

        String payloadJson = createMessageWebhookJson("psid-sec-valid", "Xin chào");
        String validSignature = "sha256=" + computeHmacSha256(payloadJson, properties.getAppSecret());

        boolean result = messengerService.handleWebhookPayload(validSignature, payloadJson);
        assertThat(result).isTrue();
        verify(customerChatService).chat(any());
    }

    @Test
    @DisplayName("Khi verifySignature=true nhưng appSecret để trống -> bỏ qua kiểm tra chữ ký an toàn")
    void handleWebhookPayloadBypassesSignatureWhenAppSecretEmpty() {
        properties.setVerifySignature(true);
        properties.setAppSecret("");
        String payloadJson = createMessageWebhookJson("psid-sec-nosecret", "Hello");

        boolean result = messengerService.handleWebhookPayload(null, payloadJson);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("handleWebhookPayload khi verifySignature=false -> cho phép thiếu header X-Hub-Signature-256")
    void handleWebhookPayloadAllowsMissingSignatureWhenVerifySignatureDisabled() {
        properties.setVerifySignature(false);
        String payload = "{\"object\":\"page\",\"entry\":[]}";

        boolean result = messengerService.handleWebhookPayload(null, payload);
        assertThat(result).isTrue();
    }

    // --- 3. Text Message Handling & Graph Client Dispatch ---

    @Test
    @DisplayName("Xử lý tin nhắn text -> gọi CustomerChatService và gửi reply kèm quick replies qua GraphClient")
    void handleTextMessageRoutesToCustomerChatAndSendsGraphReply() {
        CustomerChatResponse chatResponse = new CustomerChatResponse(
                "Dạ em chào anh! Gói Pro hiện có giá 499.000đ/tháng [1].",
                "Khách hàng mua hàng",
                "SALES",
                "customer-sales-service-v1",
                "9router:codex/gpt-6-astra",
                List.of("Quy trình đặt mua và kích hoạt gói Pro mất bao lâu?"),
                List.of(new Citation("c1", "d1", List.of(), 1, List.of(), List.of(), "Trích dẫn gói Pro")),
                "ix-sales-101",
                1,
                1
        );
        when(customerChatService.chat(any(CustomerChatRequest.class))).thenReturn(chatResponse);
        when(graphClient.sendTextMessage(any(), any(), anyList()))
                .thenReturn(new SendResult(true, "mid-101", "SENT"));

        String payloadJson = createMessageWebhookJson("psid-user-01", "Tư vấn gói Pro giúp mình");
        boolean handled = messengerService.handleWebhookPayload(null, payloadJson);

        assertThat(handled).isTrue();

        // Kiểm tra CustomerChatService được gọi đúng câu hỏi
        ArgumentCaptor<CustomerChatRequest> reqCaptor = ArgumentCaptor.forClass(CustomerChatRequest.class);
        verify(customerChatService).chat(reqCaptor.capture());
        assertThat(reqCaptor.getValue().resolvedQuestion()).isEqualTo("Tư vấn gói Pro giúp mình");

        // Kiểm tra GraphClient được gọi gửi câu trả lời
        ArgumentCaptor<String> recipientCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<MessengerSendRequest.QuickReplyItem>> qrCaptor = ArgumentCaptor.forClass(List.class);

        verify(graphClient).sendTextMessage(recipientCaptor.capture(), textCaptor.capture(), qrCaptor.capture());
        assertThat(recipientCaptor.getValue()).isEqualTo("psid-user-01");
        assertThat(textCaptor.getValue()).contains("Gói Pro hiện có giá 499.000đ/tháng");

        // Kiểm tra Quick Replies: câu hỏi gợi ý bị cắt <= 20 ký tự, và có 2 nút 👍/👎
        List<MessengerSendRequest.QuickReplyItem> quickReplies = qrCaptor.getValue();
        assertThat(quickReplies).isNotEmpty();
        assertThat(quickReplies.get(0).title().length()).isLessThanOrEqualTo(20);
        assertThat(quickReplies.get(0).payload()).isEqualTo("Quy trình đặt mua và kích hoạt gói Pro mất bao lâu?");

        // Kiểm tra 2 nút feedback
        assertThat(quickReplies).anyMatch(qr -> qr.payload().equals("FEEDBACK_HELPFUL_ix-sales-101"));
        assertThat(quickReplies).anyMatch(qr -> qr.payload().equals("FEEDBACK_UNHELPFUL_ix-sales-101"));
    }

    // --- 4. Multi-Turn Session & Turn Eviction ---

    @Test
    @DisplayName("Hội thoại đa lượt tích lũy lịch sử và cắt tỉa theo sessionMaxTurns")
    void multiTurnSessionAccumulatesAndTruncatesHistory() {
        properties.setSessionMaxTurns(3); // Giới hạn chỉ giữ 3 lượt gần nhất

        CustomerChatResponse resp1 = new CustomerChatResponse(
                "Chào anh! Em có thể giúp gì cho anh?", "Khách hàng", "GENERAL", "t1", "m1", List.of(), List.of(), "ix-1", 0, 0);
        CustomerChatResponse resp2 = new CustomerChatResponse(
                "Dạ gói Pro có giá 499k.", "Khách hàng", "SALES", "t1", "m1", List.of(), List.of(), "ix-2", 0, 0);
        CustomerChatResponse resp3 = new CustomerChatResponse(
                "Dạ bảo hành 12 tháng ạ.", "Khách hàng", "WARRANTY_RETURN", "t1", "m1", List.of(), List.of(), "ix-3", 0, 0);
        CustomerChatResponse resp4 = new CustomerChatResponse(
                "Dạ thanh toán qua QR Pay.", "Khách hàng", "ORDER_BILLING", "t1", "m1", List.of(), List.of(), "ix-4", 0, 0);

        when(customerChatService.chat(any())).thenReturn(resp1, resp2, resp3, resp4);
        when(graphClient.sendTextMessage(any(), any(), anyList()))
                .thenReturn(new SendResult(true, "mid", "SENT"));

        String senderId = "psid-multi-turn";

        // Turn 1
        messengerService.handleWebhookPayload(null, createMessageWebhookJson(senderId, "Chào shop"));
        // Turn 2
        messengerService.handleWebhookPayload(null, createMessageWebhookJson(senderId, "Gói Pro bao nhiêu?"));
        // Turn 3
        messengerService.handleWebhookPayload(null, createMessageWebhookJson(senderId, "Bảo hành ra sao?"));
        // Turn 4
        messengerService.handleWebhookPayload(null, createMessageWebhookJson(senderId, "Thanh toán thế nào?"));

        ArgumentCaptor<CustomerChatRequest> reqCaptor = ArgumentCaptor.forClass(CustomerChatRequest.class);
        verify(customerChatService, times(4)).chat(reqCaptor.capture());

        CustomerChatRequest lastRequest = reqCaptor.getAllValues().get(3);
        List<CustomerChatRequest.ConversationTurn> history = lastRequest.history();

        // Vì sessionMaxTurns = 3, history không được vượt quá 3 turns gần nhất
        assertThat(history).isNotNull();
        assertThat(history.size()).isLessThanOrEqualTo(3 * 2); // Tối đa 3 cặp customer+assistant
    }

    // --- 5. Quick Reply Title 20-Character Constraint ---

    @Test
    @DisplayName("Quick reply title bắt buộc <= 20 ký tự theo quy định cứng của Meta")
    void quickReplyTitlesStrictlyBoundedTo20Chars() {
        String longQuestion = "Tôi nên chọn gói sản phẩm/dịch vụ nào phù hợp với nhu cầu cá nhân?";
        assertThat(longQuestion.length()).isGreaterThan(20);

        CustomerChatResponse chatResp = new CustomerChatResponse(
                "Câu trả lời", "Role", "SALES", "t1", "m1",
                List.of(longQuestion), List.of(), "ix-long-01", 1, 0);
        when(customerChatService.chat(any())).thenReturn(chatResp);
        when(graphClient.sendTextMessage(any(), any(), anyList())).thenReturn(new SendResult(true, "m", "SENT"));

        messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-qr-test", "Hỏi"));

        ArgumentCaptor<List<MessengerSendRequest.QuickReplyItem>> qrCaptor = ArgumentCaptor.forClass(List.class);
        verify(graphClient).sendTextMessage(any(), any(), qrCaptor.capture());

        List<MessengerSendRequest.QuickReplyItem> items = qrCaptor.getValue();
        for (MessengerSendRequest.QuickReplyItem item : items) {
            assertThat(item.title().length())
                    .as("Quick reply title '%s' must not exceed 20 characters", item.title())
                    .isLessThanOrEqualTo(20);
        }
    }

    // --- 6. Postback Feedback to EvoAgentX Self-Evolution ---

    @Test
    @DisplayName("Postback FEEDBACK_HELPFUL kích hoạt CustomerSelfEvolutionService.recordFeedbackAndEvolve với helpful=true, rating=5")
    void feedbackHelpfulPostbackTriggersEvolutionService() {
        CustomerEvolutionStatusResponse evoStatus = new CustomerEvolutionStatusResponse(
                true, "evoagentx-textgrad-mipro", 2, 5, 2, 0.9, Map.of(), Map.of(), List.of());
        when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoStatus);
        when(graphClient.sendTextMessage(any(), any(), anyList())).thenReturn(new SendResult(true, "m", "SENT"));

        String postbackJson = createPostbackWebhookJson("psid-fb-user", "👍 Hài lòng", "FEEDBACK_HELPFUL_ix-test-888");
        messengerService.handleWebhookPayload(null, postbackJson);

        ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
        verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

        CustomerFeedbackRequest fbReq = fbCaptor.getValue();
        assertThat(fbReq.interactionId()).isEqualTo("ix-test-888");
        assertThat(fbReq.helpful()).isTrue();
        assertThat(fbReq.rating()).isEqualTo(5);

        // Xác nhận có gửi tin nhắn cảm ơn lại cho khách hàng
        verify(graphClient).sendTextMessage(eq("psid-fb-user"), any(), any());
    }

    @Test
    @DisplayName("Postback FEEDBACK_UNHELPFUL kích hoạt recordFeedbackAndEvolve với helpful=false, rating=1")
    void feedbackUnhelpfulPostbackTriggersNegativeEvolution() {
        CustomerEvolutionStatusResponse evoStatus = new CustomerEvolutionStatusResponse(
                true, "evoagentx-textgrad-mipro", 2, 5, 2, 0.8, Map.of(), Map.of(), List.of());
        when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoStatus);
        when(graphClient.sendTextMessage(any(), any(), anyList())).thenReturn(new SendResult(true, "m", "SENT"));

        String postbackJson = createPostbackWebhookJson("psid-fb-user", "👎 Cần cải thiện", "FEEDBACK_UNHELPFUL_ix-test-888");
        messengerService.handleWebhookPayload(null, postbackJson);

        ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
        verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

        CustomerFeedbackRequest fbReq = fbCaptor.getValue();
        assertThat(fbReq.interactionId()).isEqualTo("ix-test-888");
        assertThat(fbReq.helpful()).isFalse();
        assertThat(fbReq.rating()).isEqualTo(1);
    }

    @Test
    @DisplayName("Feedback với interactionId bị null/trống vẫn gửi tin nhắn cảm ơn khách hàng và không ném ngoại lệ")
    void feedbackWithNullOrBlankInteractionIdAcknowledgesCustomerGracefully() {
        when(graphClient.sendTextMessage(any(), any(), anyList()))
                .thenReturn(new SendResult(true, "mid-ack-1", "SENT"));

        String postbackJson = createPostbackWebhookJson("psid-no-session", "👍 Hài lòng", "FEEDBACK_HELPFUL_");

        boolean result = messengerService.handleWebhookPayload(null, postbackJson);
        assertThat(result).isTrue();

        verify(evolutionService, never()).recordFeedbackAndEvolve(any());
        verify(graphClient).sendTextMessage(eq("psid-no-session"), contains("DocMind xin cảm ơn"), anyList());
    }

    @Test
    @DisplayName("Feedback unhelpful khi session bị LRU evict vẫn gửi tin nhắn tiếp thu cho khách hàng")
    void feedbackUnhelpfulWithEvictedSessionStillAcknowledgesCustomer() {
        when(graphClient.sendTextMessage(any(), any(), anyList()))
                .thenReturn(new SendResult(true, "mid-ack-2", "SENT"));

        String postbackJson = createPostbackWebhookJson("psid-evicted-user", "👎 Cần cải thiện", "FEEDBACK_UNHELPFUL_   ");
        boolean result = messengerService.handleWebhookPayload(null, postbackJson);
        assertThat(result).isTrue();

        verify(evolutionService, never()).recordFeedbackAndEvolve(any());
        verify(graphClient).sendTextMessage(eq("psid-evicted-user"), contains("chưa hỗ trợ trọn vẹn"), anyList());
    }

    @Test
    @DisplayName("Lỗi phát sinh trong evolutionService không làm gián đoạn việc gửi tin nhắn cảm ơn khách hàng")
    void evolutionServiceExceptionDoesNotBlockCustomerAcknowledgment() {
        when(evolutionService.recordFeedbackAndEvolve(any()))
                .thenThrow(new RuntimeException("Simulated EvoAgentX DB failure"));
        when(graphClient.sendTextMessage(any(), any(), anyList()))
                .thenReturn(new SendResult(true, "mid-ack-3", "SENT"));

        String postbackJson = createPostbackWebhookJson("psid-err-user", "👍 Hài lòng", "FEEDBACK_HELPFUL_ix-valid-99");
        boolean result = messengerService.handleWebhookPayload(null, postbackJson);
        assertThat(result).isTrue();

        verify(graphClient).sendTextMessage(eq("psid-err-user"), any(), anyList());
    }

    // --- 7. Reaction Handling ---

    @Test
    @DisplayName("Sự kiện Reaction love/like kích hoạt học hỏi tích cực, angry/sad kích hoạt học hỏi điều chỉnh")
    void reactionEventMappedToEvolutionFeedback() {
        CustomerEvolutionStatusResponse evoStatus = new CustomerEvolutionStatusResponse(
                true, "evoagentx-textgrad-mipro", 1, 1, 1, 0.95, Map.of(), Map.of(), List.of());
        when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoStatus);

        // Giả lập session đã có interactionId trước đó
        CustomerChatResponse resp = new CustomerChatResponse(
                "Câu trả lời", "Role", "SALES", "t1", "m1", List.of(), List.of(), "ix-react-001", 1, 0);
        when(customerChatService.chat(any())).thenReturn(resp);
        when(graphClient.sendTextMessage(any(), any(), anyList())).thenReturn(new SendResult(true, "m", "SENT"));
        messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-reactor", "Chào shop"));

        // Gửi reaction "love"
        String reactionJson = createReactionWebhookJson("psid-reactor", "love", "❤️");
        messengerService.handleWebhookPayload(null, reactionJson);

        ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
        verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

        assertThat(fbCaptor.getValue().interactionId()).isEqualTo("ix-react-001");
        assertThat(fbCaptor.getValue().helpful()).isTrue();
        assertThat(fbCaptor.getValue().rating()).isEqualTo(5);
    }

    @Test
    @DisplayName("Reaction khi không có session hợp lệ không ném ngoại lệ và không gọi evolutionService")
    void reactionWithNullSessionDoesNotThrowAndSkipsEvolution() {
        String reactionJson = createReactionWebhookJson("psid-no-history", "like", "👍");

        boolean result = messengerService.handleWebhookPayload(null, reactionJson);
        assertThat(result).isTrue();

        verify(evolutionService, never()).recordFeedbackAndEvolve(any());
    }

    // --- 8. Simulation Mode & Status ---

    @Test
    @DisplayName("simulateMessage chạy end-to-end độc lập ngay cả khi chưa có pageAccessToken")
    void simulateMessageEndToEnd() {
        properties.setPageAccessToken(""); // Chưa có token
        CustomerChatResponse chatResp = new CustomerChatResponse(
                "Dạ em chào Anh Khoa! Gói Enterprise phù hợp cho quy mô trên 50 người [1].",
                "Khách hàng",
                "SALES",
                "customer-sales-service-v1",
                "stub-llm",
                List.of("Báo giá gói năm"),
                List.of(),
                "ix-sim-enterprise",
                2,
                1
        );
        when(customerChatService.chat(any())).thenReturn(chatResp);

        MessengerSimulateRequest req = new MessengerSimulateRequest("sim-user-1", "Tư vấn gói Enterprise", "SALES");
        MessengerSimulateResponse resp = messengerService.simulateMessage(req);

        assertThat(resp.senderId()).isEqualTo("sim-user-1");
        assertThat(resp.replySent()).contains("Gói Enterprise phù hợp");
        assertThat(resp.simulated()).isTrue();
        assertThat(resp.interactionId()).isEqualTo("ix-sim-enterprise");
        assertThat(resp.quickReplies()).isNotEmpty();

        // Kiểm tra GraphClient KHÔNG bị gọi ra ngoài
        verify(graphClient, never()).sendTextMessage(any(), any(), any());

        // Kiểm tra status hiển thị đúng số phiên và tương tác vừa lưu
        MessengerStatusResponse status = messengerService.getStatus();
        assertThat(status.activeSessionsCount()).isGreaterThanOrEqualTo(1);
        assertThat(status.recentInteractions()).anyMatch(i -> i.interactionId().equals("ix-sim-enterprise"));
    }

    // --- 9. LRU Session Eviction ---

    @Test
    @DisplayName("Giới hạn số phiên tối đa theo maxSessions theo cơ chế LRU")
    void lruSessionEvictionBoundsMemory() {
        properties.setMaxSessions(2); // Chỉ giữ tối đa 2 sessions

        CustomerChatResponse dummyResp = new CustomerChatResponse(
                "Trả lời", "Role", "SALES", "t1", "m1", List.of(), List.of(), "ix", 0, 0);
        when(customerChatService.chat(any())).thenReturn(dummyResp);
        when(graphClient.sendTextMessage(any(), any(), anyList())).thenReturn(new SendResult(true, "m", "SENT"));

        messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-A", "Chat A"));
        messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-B", "Chat B"));
        assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(2);

        // Thêm user-C -> user-A cũ nhất bị evict
        messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-C", "Chat C"));
        assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(2);
    }

    // --- Helper Methods ---

    private static String computeHmacSha256(String data, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(keySpec);
        byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(rawHmac);
    }

    private static String createMessageWebhookJson(String senderId, String text) {
        return """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page-1",
                      "time": 1728882000000,
                      "messaging": [
                        {
                          "sender": { "id": "%s" },
                          "recipient": { "id": "page-1" },
                          "timestamp": 1728882000000,
                          "message": { "mid": "mid-%d", "text": "%s" }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(senderId, System.currentTimeMillis(), text);
    }

    private static String createPostbackWebhookJson(String senderId, String title, String payload) {
        return """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page-1",
                      "time": 1728882000000,
                      "messaging": [
                        {
                          "sender": { "id": "%s" },
                          "recipient": { "id": "page-1" },
                          "timestamp": 1728882000000,
                          "postback": { "title": "%s", "payload": "%s" }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(senderId, title, payload);
    }

    private static String createReactionWebhookJson(String senderId, String reaction, String emoji) {
        return """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page-1",
                      "time": 1728882000000,
                      "messaging": [
                        {
                          "sender": { "id": "%s" },
                          "recipient": { "id": "page-1" },
                          "timestamp": 1728882000000,
                          "reaction": { "reaction": "%s", "emoji": "%s", "action": "react", "mid": "mid-1" }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(senderId, reaction, emoji);
    }
}

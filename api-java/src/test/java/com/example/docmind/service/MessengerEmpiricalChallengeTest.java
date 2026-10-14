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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Empirical Stress Test Suite for Milestone 1 Facebook Messenger Integration.
 * Executed independently by Challenger M1.1 to verify robustness against adversarial inputs.
 */
class MessengerEmpiricalChallengeTest {

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
        properties.setVerifyToken("adversarial-verify-token");
        properties.setAppSecret("c4ca4238a0b923820dcc509a6f75849b");
        properties.setVerifySignature(false);
        properties.setPageAccessToken("adversarial-page-token");
        properties.setMaxQuickReplies(5);
        properties.setMaxQuickReplyTitleChars(20);
        properties.setSessionMaxTurns(5);
        properties.setMaxSessions(10);
        properties.setSendFeedbackQuickReplies(true);

        messengerService = new MessengerService(
                properties, customerChatService, evolutionService, graphClient);
    }

    // =========================================================================
    // 1. HMAC-SHA256 SIGNATURE TAMPERING STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("1. HMAC Signature Tampering Probes")
    class HmacSignatureTamperingTests {

        private static final String PAYLOAD = "{\"object\":\"page\",\"entry\":[{\"id\":\"1001\",\"messaging\":[]}]}";

        @BeforeEach
        void enableSignatureVerification() {
            properties.setVerifySignature(true);
        }

        @Test
        @DisplayName("Missing signature header khi verifySignature=true -> handleWebhookPayload ném SecurityException")
        void missingSignatureHeaderThrowsSecurityException() {
            assertThatThrownBy(() -> messengerService.handleWebhookPayload(null, PAYLOAD))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("Blank signature header khi verifySignature=true -> handleWebhookPayload ném SecurityException")
        void blankSignatureHeaderThrowsSecurityException() {
            assertThatThrownBy(() -> messengerService.handleWebhookPayload("   ", PAYLOAD))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("signature");
        }

        @Test
        @DisplayName("Xác thực HMAC chữ ký gốc khớp 100%")
        void authenticPayloadMatchesSignature() throws Exception {
            String signature = "sha256=" + computeHmac(PAYLOAD, properties.getAppSecret());
            assertThat(messengerService.verifySignature(signature, PAYLOAD)).isTrue();
        }

        @Test
        @DisplayName("Biến đổi 1 byte trong payload -> HMAC bị từ chối")
        void singleBytePayloadTamperingRejected() throws Exception {
            String signature = "sha256=" + computeHmac(PAYLOAD, properties.getAppSecret());
            // Đổi 1 ký tự: '1001' -> '1002'
            String tampered = PAYLOAD.replace("1001", "1002");

            assertThat(messengerService.verifySignature(signature, tampered)).isFalse();
            assertThatThrownBy(() -> messengerService.handleWebhookPayload(signature, tampered))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("Invalid HMAC-SHA256 signature");
        }

        @Test
        @DisplayName("Thêm 1 khoảng trắng vô hình cuối payload -> HMAC bị từ chối")
        void trailingWhitespaceTamperingRejected() throws Exception {
            String signature = "sha256=" + computeHmac(PAYLOAD, properties.getAppSecret());
            String tampered = PAYLOAD + " ";

            assertThat(messengerService.verifySignature(signature, tampered)).isFalse();
        }

        @Test
        @DisplayName("Biến đổi 1 ký tự hex trong header chữ ký -> HMAC bị từ chối")
        void singleCharSignatureTamperingRejected() throws Exception {
            String signature = "sha256=" + computeHmac(PAYLOAD, properties.getAppSecret());
            // Đổi ký tự hex đầu tiên
            char firstHex = signature.charAt(7);
            char alteredHex = (firstHex == 'a') ? 'b' : 'a';
            String tamperedSignature = signature.substring(0, 7) + alteredHex + signature.substring(8);

            assertThat(messengerService.verifySignature(tamperedSignature, PAYLOAD)).isFalse();
        }

        @Test
        @DisplayName("Chữ ký dùng sai secret key -> HMAC bị từ chối")
        void wrongSecretKeyRejected() throws Exception {
            String signature = "sha256=" + computeHmac(PAYLOAD, "completely-wrong-secret-key");
            assertThat(messengerService.verifySignature(signature, PAYLOAD)).isFalse();
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "sha1=9876543210abcdef9876543210abcdef98765432",
                "md5=abcdef1234567890",
                "sha256=",
                "sha256=   ",
                "sha256=not-a-hex",
                "invalid-format",
                ""
        })
        @DisplayName("Các header signature định dạng dị dạng đều bị từ chối")
        void malformedSignatureHeadersRejected(String malformedHeader) {
            assertThat(messengerService.verifySignature(malformedHeader, PAYLOAD)).isFalse();
        }

        @Test
        @DisplayName("Null signature hoặc null payload -> an toàn trả về false, không NPE")
        void nullInputsHandledSafely() {
            assertThat(messengerService.verifySignature(null, PAYLOAD)).isFalse();
            assertThat(messengerService.verifySignature("sha256=abcdef", null)).isFalse();
            assertThat(messengerService.verifySignature(null, null)).isFalse();
        }

        @Test
        @DisplayName("Thứ tự tham số đảo ngược (rawPayload trước, signature sau) vẫn được nhận diện chính xác")
        void invertedArgumentOrderHandledCorrectly() throws Exception {
            String signature = "sha256=" + computeHmac(PAYLOAD, properties.getAppSecret());
            // Gọi verifySignature(payload, signature)
            assertThat(messengerService.verifySignature(PAYLOAD, signature)).isTrue();
        }
    }

    // =========================================================================
    // 2. QUICK REPLY LENGTH BOUNDARY STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("2. Quick Reply Length Boundary Probes")
    class QuickReplyLengthBoundaryTests {

        @Test
        @DisplayName("Tiêu đề <= 20 ký tự được giữ nguyên nguyên vẹn")
        void titlesUnderOrEqual20CharsUnchanged() {
            assertThat(MessengerService.truncateQuickReplyTitle("Hỏi giá gói Pro", 20))
                    .isEqualTo("Hỏi giá gói Pro");
            assertThat(MessengerService.truncateQuickReplyTitle("12345678901234567890", 20))
                    .hasSize(20)
                    .isEqualTo("12345678901234567890");
        }

        @Test
        @DisplayName("Tiêu đề đúng 21 ký tự bị cắt đúng trần 20 ký tự (17 ký tự + '...')")
        void title21CharsTruncatedToExactly20Chars() {
            String title21 = "123456789012345678901"; // 21 chars
            String result = MessengerService.truncateQuickReplyTitle(title21, 20);

            assertThat(result).hasSize(20);
            assertThat(result).isEqualTo("12345678901234567...");
        }

        @Test
        @DisplayName("Tiêu đề tiếng Việt có dấu dài > 50 ký tự được cắt tỉa an toàn <= 20 ký tự")
        void longVietnameseTitlesTruncatedTo20Chars() {
            String longVietnamese = "Quy trình đăng ký và kích hoạt gói dịch vụ DocMind mất bao lâu?";
            String result = MessengerService.truncateQuickReplyTitle(longVietnamese, 20);

            assertThat(result.length()).isLessThanOrEqualTo(20);
            assertThat(result).endsWith("...");
            assertThat(result).hasSize(20);
        }

        @Test
        @DisplayName("Tiêu đề rỗng hoặc null hoặc chỉ toàn khoảng trắng trả về fallback <= 20 ký tự")
        void nullOrBlankTitleReturnsValidFallback() {
            assertThat(MessengerService.truncateQuickReplyTitle(null, 20))
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(20);

            assertThat(MessengerService.truncateQuickReplyTitle("", 20))
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(20);

            assertThat(MessengerService.truncateQuickReplyTitle("   ", 20))
                    .isNotBlank()
                    .hasSizeLessThanOrEqualTo(20);
        }

        @Test
        @DisplayName("Nhiều câu hỏi gợi ý vượt quá maxQuickReplies (5) bị giới hạn đúng 5 items")
        void quickRepliesCountCappedAtMax() {
            CustomerChatResponse chatResp = new CustomerChatResponse(
                    "Câu trả lời", "Role", "SALES", "t1", "m1",
                    List.of("Gợi ý 1", "Gợi ý 2", "Gợi ý 3", "Gợi ý 4", "Gợi ý 5", "Gợi ý 6", "Gợi ý 7"),
                    List.of(), "ix-qr-count", 1, 0);
            when(customerChatService.chat(any())).thenReturn(chatResp);
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid", "SENT"));

            properties.setMaxQuickReplies(5);
            String payload = createMessageWebhookJson("psid-qr-bound", "Xin chào");
            messengerService.handleWebhookPayload(null, payload);

            ArgumentCaptor<List<MessengerSendRequest.QuickReplyItem>> qrCaptor = ArgumentCaptor.forClass(List.class);
            verify(graphClient).sendTextMessage(any(), any(), qrCaptor.capture());

            List<MessengerSendRequest.QuickReplyItem> items = qrCaptor.getValue();
            assertThat(items).hasSizeLessThanOrEqualTo(5);
            for (var item : items) {
                assertThat(item.title().length()).isLessThanOrEqualTo(20);
            }
        }
    }

    // =========================================================================
    // 3. MALFORMED WEBHOOK JSON STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("3. Malformed Webhook JSON Probes")
    class MalformedWebhookJsonTests {

        @ParameterizedTest
        @ValueSource(strings = {
                "{ unclosed json",
                "not json at all",
                "",
                "   ",
                "[]",
                "[1, 2, 3]",
                "{\"object\": 123}",
                "{\"object\": \"page\", \"entry\": null}",
                "{\"object\": \"page\", \"entry\": []}",
                "{\"object\": \"page\", \"entry\": [null]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": null}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [null]}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [{}]}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [{\"sender\": null}]}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [{\"sender\": {\"id\": \"\"}}]}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [{\"sender\": {\"id\": \"   \"}}]}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [{\"sender\": {\"id\": \"u1\"}, \"message\": null, \"postback\": null}]}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [{\"sender\": {\"id\": \"u1\"}, \"message\": {\"text\": null}}]}]}",
                "{\"object\": \"page\", \"entry\": [{\"messaging\": [{\"sender\": {\"id\": \"u1\"}, \"message\": {\"text\": \"   \"}}]}]}",
                "{\"object\": \"user\", \"entry\": []}", // Non-page object
                "{\"object\": \"instagram\", \"entry\": []}"
        })
        @DisplayName("Mọi payload JSON dị dạng, rỗng, null hoặc cấu trúc lạ đều được xử lý êm ái (graceful), không ném ngoại lệ 500")
        void malformedJsonHandledGracefullyWithoutCrashing(String malformedPayload) {
            assertThatCode(() -> messengerService.handleWebhookPayload(null, malformedPayload))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Null rawPayload xử lý êm ái")
        void nullPayloadHandledGracefully() {
            assertThatCode(() -> messengerService.handleWebhookPayload(null, null))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Tin nhắn delivery và read receipts được bỏ qua không gọi CustomerChatService")
        void deliveryAndReadReceiptsIgnored() {
            String deliveryJson = """
                    {
                      "object": "page",
                      "entry": [{
                        "id": "p1",
                        "messaging": [{
                          "sender": {"id": "u1"},
                          "delivery": {"mids": ["m1"], "watermark": 1728882000000}
                        }]
                      }]
                    }
                    """;
            messengerService.handleWebhookPayload(null, deliveryJson);
            verify(customerChatService, never()).chat(any());
        }

        @Test
        @DisplayName("Tin nhắn echo (bot tự phát) được bỏ qua hoàn toàn để chống vòng lặp vô hạn")
        void echoMessagesIgnoredToPreventInfiniteLoop() {
            String echoJson = """
                    {
                      "object": "page",
                      "entry": [{
                        "id": "p1",
                        "messaging": [{
                          "sender": {"id": "u1"},
                          "message": {"mid": "m-echo", "text": "bot echo", "is_echo": true}
                        }]
                      }]
                    }
                    """;
            messengerService.handleWebhookPayload(null, echoJson);
            verify(customerChatService, never()).chat(any());
        }
    }

    // =========================================================================
    // 4. SIMULATOR ENDPOINT STRESS TESTS
    // =========================================================================
    @Nested
    @DisplayName("4. Simulator Endpoint Edge Probes")
    class SimulatorEndpointEdgeTests {

        @Test
        @DisplayName("Simulator với message null hoặc blank ném IllegalArgumentException")
        void nullOrBlankMessageThrowsIllegalArgumentException() {
            assertThatThrownBy(() -> messengerService.simulateMessage(new MessengerSimulateRequest(null, null, null)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> messengerService.simulateMessage(new MessengerSimulateRequest("user1", "", null)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThatThrownBy(() -> messengerService.simulateMessage(new MessengerSimulateRequest("user1", "   ", null)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Simulator không truyền senderId -> tự động cấp phát ID ngẫu nhiên bắt đầu bằng sim-user-")
        void missingSenderIdGeneratesFallbackId() {
            CustomerChatResponse chatResp = new CustomerChatResponse(
                    "Chào mừng bạn đến với DocMind!", "Khách hàng", "GENERAL", "t1", "m1",
                    List.of("Tìm hiểu dịch vụ"), List.of(), "ix-auto-sim", 1, 0);
            when(customerChatService.chat(any())).thenReturn(chatResp);

            MessengerSimulateRequest req = new MessengerSimulateRequest(null, "Xin chào", null);
            MessengerSimulateResponse resp = messengerService.simulateMessage(req);

            assertThat(resp.senderId()).isNotBlank().startsWith("sim-user-");
            assertThat(resp.receivedMessage()).isEqualTo("Xin chào");
            assertThat(resp.replySent()).contains("Chào mừng");
            assertThat(resp.simulated()).isTrue();
        }

        @Test
        @DisplayName("Simulator gửi tin nhắn siêu dài (> 5.000 ký tự tiếng Việt) hoạt động an toàn")
        void massiveTextMessageHandledSafely() {
            String massiveText = "Tôi muốn tìm hiểu chi tiết về các giải pháp OCR và RAG của DocMind. ".repeat(80);
            assertThat(massiveText.length()).isGreaterThan(5000);

            CustomerChatResponse chatResp = new CustomerChatResponse(
                    "Dạ DocMind cung cấp hệ thống RAG và OCR chuyên sâu.", "Khách hàng", "GENERAL", "t1", "m1",
                    List.of(), List.of(), "ix-massive", 1, 0);
            when(customerChatService.chat(any())).thenReturn(chatResp);

            MessengerSimulateRequest req = new MessengerSimulateRequest("sim-user-massive", massiveText, "GENERAL");
            MessengerSimulateResponse resp = messengerService.simulateMessage(req);

            assertThat(resp.receivedMessage()).isEqualTo(massiveText.strip());
            assertThat(resp.replySent()).isNotBlank();
        }

        @Test
        @DisplayName("Hội thoại giả lập liên tiếp bảo toàn ngữ cảnh đa lượt giữa các lượt gọi")
        void consecutiveSimulateCallsPreserveMultiTurnHistory() {
            CustomerChatResponse resp1 = new CustomerChatResponse(
                    "Dạ em chào anh!", "Khách hàng", "GENERAL", "t1", "m1", List.of(), List.of(), "ix-1", 0, 0);
            CustomerChatResponse resp2 = new CustomerChatResponse(
                    "Gói Pro giá 499k/tháng.", "Khách hàng", "SALES", "t1", "m1", List.of(), List.of(), "ix-2", 0, 0);

            when(customerChatService.chat(any())).thenReturn(resp1, resp2);

            String senderId = "sim-multi-turn-01";
            messengerService.simulateMessage(new MessengerSimulateRequest(senderId, "Chào em", null));
            messengerService.simulateMessage(new MessengerSimulateRequest(senderId, "Gói Pro bao nhiêu tiền?", null));

            ArgumentCaptor<CustomerChatRequest> reqCaptor = ArgumentCaptor.forClass(CustomerChatRequest.class);
            verify(customerChatService, org.mockito.Mockito.times(2)).chat(reqCaptor.capture());

            // Lượt 2 phải chứa lịch sử của lượt 1
            CustomerChatRequest secondReq = reqCaptor.getAllValues().get(1);
            assertThat(secondReq.history()).isNotEmpty();
            assertThat(secondReq.history()).anyMatch(turn -> turn.content().equals("Chào em"));
        }

        @Test
        @DisplayName("Cơ chế cắt tỉa LRU hoạt động chuẩn khi vượt quá maxSessions")
        void lruEvictionBoundsMemoryUnderHeavyLoad() {
            properties.setMaxSessions(5); // Giới hạn chỉ 5 sessions

            CustomerChatResponse dummy = new CustomerChatResponse(
                    "Trả lời", "Khách hàng", "SALES", "t1", "m1", List.of(), List.of(), "ix", 0, 0);
            when(customerChatService.chat(any())).thenReturn(dummy);

            // Tạo 10 user khác nhau
            for (int i = 1; i <= 10; i++) {
                messengerService.simulateMessage(new MessengerSimulateRequest("user-" + i, "Tin " + i, null));
            }

            MessengerStatusResponse status = messengerService.getStatus();
            assertThat(status.activeSessionsCount()).isEqualTo(5);
        }
    }

    // =========================================================================
    // 5. FEEDBACK & REACTION NULL-SAFETY EMPIRICAL PROBES
    // =========================================================================
    @Nested
    @DisplayName("5. Feedback & Reaction Null Safety Empirical Probes")
    class FeedbackAndReactionNullSafetyTests {

        @Test
        @DisplayName("Quick reply FEEDBACK_HELPFUL_ với interactionId null (không có session) -> Khách hàng vẫn nhận ACK cảm ơn, không gọi evolutionService")
        void nullInteractionIdInQuickReplyHelpful_StillAcknowledgesCustomer() {
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid-ack-qr-1", "SENT"));

            String qrJson = createQuickReplyWebhookJson("psid-null-qr-helpful", "FEEDBACK_HELPFUL_");
            boolean handled = messengerService.handleWebhookPayload(null, qrJson);

            assertThat(handled).isTrue();
            verify(evolutionService, never()).recordFeedbackAndEvolve(any());
            verify(graphClient).sendTextMessage(
                    eq("psid-null-qr-helpful"),
                    contains("DocMind xin cảm ơn"),
                    anyList()
            );
        }

        @Test
        @DisplayName("Quick reply FEEDBACK_UNHELPFUL_ với interactionId null (không có session) -> Khách hàng vẫn nhận ACK tiếp thu, không gọi evolutionService")
        void nullInteractionIdInQuickReplyUnhelpful_StillAcknowledgesCustomer() {
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid-ack-qr-2", "SENT"));

            String qrJson = createQuickReplyWebhookJson("psid-null-qr-unhelpful", "FEEDBACK_UNHELPFUL_");
            boolean handled = messengerService.handleWebhookPayload(null, qrJson);

            assertThat(handled).isTrue();
            verify(evolutionService, never()).recordFeedbackAndEvolve(any());
            verify(graphClient).sendTextMessage(
                    eq("psid-null-qr-unhelpful"),
                    contains("chưa hỗ trợ trọn vẹn"),
                    anyList()
            );
        }

        @Test
        @DisplayName("Postback FEEDBACK_HELPFUL_    (toàn khoảng trắng) -> Khách hàng vẫn nhận ACK cảm ơn, an toàn không ngoại lệ")
        void blankInteractionIdInPostbackHelpful_StillAcknowledgesCustomer() {
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid-ack-pb-1", "SENT"));

            String pbJson = createPostbackWebhookJson("psid-blank-pb-helpful", "👍 Hài lòng", "FEEDBACK_HELPFUL_   ");
            boolean handled = messengerService.handleWebhookPayload(null, pbJson);

            assertThat(handled).isTrue();
            verify(evolutionService, never()).recordFeedbackAndEvolve(any());
            verify(graphClient).sendTextMessage(
                    eq("psid-blank-pb-helpful"),
                    contains("DocMind xin cảm ơn"),
                    anyList()
            );
        }

        @Test
        @DisplayName("Postback FEEDBACK_UNHELPFUL_    (toàn khoảng trắng) -> Khách hàng vẫn nhận ACK tiếp thu, an toàn không ngoại lệ")
        void blankInteractionIdInPostbackUnhelpful_StillAcknowledgesCustomer() {
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid-ack-pb-2", "SENT"));

            String pbJson = createPostbackWebhookJson("psid-blank-pb-unhelpful", "👎 Cần cải thiện", "FEEDBACK_UNHELPFUL_   ");
            boolean handled = messengerService.handleWebhookPayload(null, pbJson);

            assertThat(handled).isTrue();
            verify(evolutionService, never()).recordFeedbackAndEvolve(any());
            verify(graphClient).sendTextMessage(
                    eq("psid-blank-pb-unhelpful"),
                    contains("chưa hỗ trợ trọn vẹn"),
                    anyList()
            );
        }

        @Test
        @DisplayName("Session bị LRU evict và tương tác bị xóa khỏi recentInteractions -> Feedback click vẫn gửi ACK và không crash 500")
        void evictedSessionWithFeedbackQuickReply_WithoutRecentInteractions_AcknowledgesCustomerGracefully() {
            properties.setMaxSessions(2);
            properties.setMaxRecentInteractions(2);

            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("Trả lời", "Role", "GENERAL", "t", "m", List.of(), List.of(), "ix-evict-old", 1, 0)
            );
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid-sent", "SENT"));

            // 1. User A tương tác
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-evicted-A", "Câu hỏi 1"));

            // 2. Các User khác đẩy User A ra khỏi cả sessionMap và recentInteractions
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-B", "Câu hỏi B"));
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-C", "Câu hỏi C"));
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-D", "Câu hỏi D"));

            // 3. User A click feedback nút trống
            String emptyFeedbackQr = createQuickReplyWebhookJson("psid-user-evicted-A", "FEEDBACK_HELPFUL_");
            boolean handled = messengerService.handleWebhookPayload(null, emptyFeedbackQr);

            assertThat(handled).isTrue();
            // Khách hàng A vẫn nhận được tin nhắn cảm ơn dù session và recentInteractions đều không còn
            verify(graphClient).sendTextMessage(eq("psid-user-evicted-A"), contains("DocMind xin cảm ơn"), anyList());
        }

        @Test
        @DisplayName("Session bị LRU evict nhưng Postback chứa interactionId hợp lệ -> Tiến hoá thành công và gửi ACK")
        void evictedSessionWithFeedbackPostback_WithPayloadInteractionId_EvolvesAndAcknowledgesCustomer() {
            CustomerEvolutionStatusResponse evoResp = new CustomerEvolutionStatusResponse(
                    true, "evo-engine", 1, 1, 1, 0.9, Map.of(), Map.of(), List.of());
            when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoResp);
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid-ack", "SENT"));

            // User không có trong sessionMap
            String pbJson = createPostbackWebhookJson("psid-evicted-but-has-id", "👍", "FEEDBACK_HELPFUL_ix-persisted-456");
            boolean handled = messengerService.handleWebhookPayload(null, pbJson);

            assertThat(handled).isTrue();
            ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

            CustomerFeedbackRequest req = fbCaptor.getValue();
            assertThat(req.interactionId()).isEqualTo("ix-persisted-456");
            assertThat(req.helpful()).isTrue();
            assertThat(req.rating()).isEqualTo(5);

            verify(graphClient).sendTextMessage(eq("psid-evicted-but-has-id"), contains("DocMind xin cảm ơn"), anyList());
        }

        @Test
        @DisplayName("Session bị LRU evict nhưng recentInteractions còn lưu -> Cứu cánh thành công interactionId cho feedback")
        void evictedSessionWithFeedbackPostback_RecoverableFromRecentInteractions_EvolvesAndAcknowledgesCustomer() {
            properties.setMaxSessions(1); // Chỉ giữ 1 session
            properties.setMaxRecentInteractions(10); // Nhưng giữ 10 recent interactions

            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("Ans", "Role", "SALES", "t", "m", List.of(), List.of(), "ix-recent-rescued-99", 1, 0));
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid", "SENT"));

            // 1. User X tương tác -> lưu vào recentInteractions
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-X", "Tư vấn gói Pro"));

            // 2. User Y tương tác -> evict User X khỏi sessionMap (vì maxSessions=1)
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-Y", "Tư vấn gói Khác"));

            // 3. User X gửi feedback không có ID -> fallback cứu cánh từ recentInteractions
            CustomerEvolutionStatusResponse evoResp = new CustomerEvolutionStatusResponse(
                    true, "evo-engine", 1, 1, 1, 0.9, Map.of(), Map.of(), List.of());
            when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoResp);

            String feedbackPayload = createPostbackWebhookJson("psid-user-X", "👍", "FEEDBACK_HELPFUL_");
            boolean handled = messengerService.handleWebhookPayload(null, feedbackPayload);

            assertThat(handled).isTrue();
            ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

            assertThat(fbCaptor.getValue().interactionId()).isEqualTo("ix-recent-rescued-99");
            verify(graphClient).sendTextMessage(eq("psid-user-X"), contains("DocMind xin cảm ơn"), anyList());
        }

        @Test
        @DisplayName("Reaction từ user không có session và không có recentInteractions -> An toàn bỏ qua không ném ngoại lệ")
        void reactionWithNullSessionAndNoRecentInteractions_SkipsEvolutionSafelyWithoutException() {
            String reactionJson = createReactionWebhookJson("psid-unknown-reactor", "love", "❤️");

            boolean handled = messengerService.handleWebhookPayload(null, reactionJson);
            assertThat(handled).isTrue();

            verify(evolutionService, never()).recordFeedbackAndEvolve(any());
        }

        @Test
        @DisplayName("Reaction từ user khi session bị evict nhưng có recentInteractions -> Nhận diện interactionId thành công")
        void reactionWithEvictedSession_RecoversInteractionIdFromRecentInteractions() {
            properties.setMaxSessions(1);
            properties.setMaxRecentInteractions(10);

            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("Ans", "Role", "SALES", "t", "m", List.of(), List.of(), "ix-react-rescued", 1, 0));
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenReturn(new SendResult(true, "mid", "SENT"));

            // User 1 chat
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-1", "Hỏi"));
            // User 2 chat (evicts User 1 from sessionMap)
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-user-2", "Hỏi tiếp"));

            // User 1 thả reaction 👍
            CustomerEvolutionStatusResponse evoResp = new CustomerEvolutionStatusResponse(
                    true, "evo-engine", 1, 1, 1, 0.9, Map.of(), Map.of(), List.of());
            when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoResp);

            String reactionJson = createReactionWebhookJson("psid-user-1", "like", "👍");
            boolean handled = messengerService.handleWebhookPayload(null, reactionJson);

            assertThat(handled).isTrue();
            ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());
            assertThat(fbCaptor.getValue().interactionId()).isEqualTo("ix-react-rescued");
            assertThat(fbCaptor.getValue().helpful()).isTrue();
        }

        @Test
        @DisplayName("Reaction với trường reaction/emoji null -> Xử lý êm dịu không NullPointerException")
        void reactionWithNullFieldsHandledSafely() {
            String nullReactionJson = """
                    {
                      "object": "page",
                      "entry": [{
                        "id": "p1",
                        "messaging": [{
                          "sender": {"id": "psid-null-reaction"},
                          "reaction": {"action": "react", "mid": "mid-1"}
                        }]
                      }]
                    }
                    """;
            boolean handled = messengerService.handleWebhookPayload(null, nullReactionJson);
            assertThat(handled).isTrue();
            verify(evolutionService, never()).recordFeedbackAndEvolve(any());
        }

        @Test
        @DisplayName("Khi GraphClient gặp sự cố mạng lúc gửi tin nhắn ACK feedback -> Không crash webhook, controller vẫn nhận true")
        void facebookGraphClientSendFailureDuringFeedbackAck_HandledGracefullyWithoutCrashing() {
            when(graphClient.sendTextMessage(any(), any(), anyList()))
                    .thenThrow(new RuntimeException("Simulated Meta Graph API 500 error"));

            String qrJson = createQuickReplyWebhookJson("psid-network-down", "FEEDBACK_HELPFUL_");
            boolean handled = messengerService.handleWebhookPayload(null, qrJson);

            assertThat(handled).isTrue();
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private static String computeHmac(String data, String secret) throws Exception {
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

    private static String createQuickReplyWebhookJson(String senderId, String payload) {
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
                          "message": {
                            "mid": "mid-qr-1",
                            "text": "👍 Hài lòng",
                            "quick_reply": { "payload": "%s" }
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(senderId, payload);
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

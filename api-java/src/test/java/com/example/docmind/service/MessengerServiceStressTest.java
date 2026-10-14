package com.example.docmind.service;

import com.example.docmind.config.MessengerProperties;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.dto.messenger.MessengerSendRequest;
import com.example.docmind.dto.messenger.MessengerSessionClearResponse;
import com.example.docmind.dto.messenger.MessengerSimulateRequest;
import com.example.docmind.dto.messenger.MessengerSimulateResponse;
import com.example.docmind.dto.messenger.MessengerStatusResponse;
import com.example.docmind.dto.messenger.SendResult;
import com.example.docmind.service.evolution.CustomerSelfEvolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Empirical Stress Test Harness for MessengerService:
 * 1. Multi-turn turn eviction (sessionMaxTurns).
 * 2. LRU cache capacity limits & memory boundedness (maxSessions).
 * 3. Feedback routing & EvoAgentX integration.
 * 4. Session clearing (individual & bulk).
 * 5. Concurrent multi-threaded safety.
 */
class MessengerServiceStressTest {

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
        properties.setVerifyToken("test-token");
        properties.setAppSecret("test-secret");
        properties.setVerifySignature(false);
        properties.setPageAccessToken("test-page-token");
        properties.setSessionMaxTurns(10);
        properties.setMaxSessions(100);
        properties.setMaxRecentInteractions(50);
        properties.setSendFeedbackQuickReplies(true);

        when(graphClient.sendTextMessage(any(), any(), anyList()))
                .thenReturn(new SendResult(true, "mid-stress", "SENT"));

        messengerService = new MessengerService(
                properties, customerChatService, evolutionService, graphClient);
    }

    // =========================================================================
    // 1. Multi-turn Turn Eviction Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("1. Multi-turn Turn Eviction Stress Tests")
    class MultiTurnEvictionTests {

        @Test
        @DisplayName("Feeding 15 consecutive turns into a session with sessionMaxTurns=10 leaves exactly newest 10 turns (oldest 5 evicted)")
        void fifteenTurnsIntoSessionMaxTurnsTenLeavesExactlyNewestTenTurns() {
            properties.setSessionMaxTurns(10);

            // Mock responses for 15 turns
            when(customerChatService.chat(any())).thenAnswer(invocation -> {
                CustomerChatRequest req = invocation.getArgument(0);
                String q = req.resolvedQuestion();
                return new CustomerChatResponse(
                        "Answer for " + q,
                        "Khách hàng",
                        "SALES",
                        "test-template",
                        "test-model",
                        List.of("Gợi ý cho " + q),
                        List.of(),
                        "ix-" + q.replace(" ", "-"),
                        1,
                        0
                );
            });

            String psid = "psid-stress-15-turns";

            // Execute 15 turns sequentially
            for (int i = 1; i <= 15; i++) {
                String question = "Question Turn " + i;
                String payload = createMessageWebhookJson(psid, question);
                messengerService.handleWebhookPayload(null, payload);
            }

            ArgumentCaptor<CustomerChatRequest> reqCaptor = ArgumentCaptor.forClass(CustomerChatRequest.class);
            verify(customerChatService, times(15)).chat(reqCaptor.capture());

            List<CustomerChatRequest> capturedRequests = reqCaptor.getAllValues();
            assertThat(capturedRequests).hasSize(15);

            // Verify Turn 1 request has empty history
            assertThat(capturedRequests.get(0).history()).isEmpty();

            // Verify Turn 11 request: had 10 turns before it, so history size = 20 (turns 1..10)
            assertThat(capturedRequests.get(10).history()).hasSize(20);
            assertThat(capturedRequests.get(10).history().get(0).content()).isEqualTo("Question Turn 1");

            // Verify Turn 15 request: should have exactly 10 turns in history (turns 5..14)
            // Turns 1..4 evicted!
            CustomerChatRequest lastRequest = capturedRequests.get(14);
            assertThat(lastRequest.resolvedQuestion()).isEqualTo("Question Turn 15");
            List<CustomerChatRequest.ConversationTurn> historyBeforeTurn15 = lastRequest.history();
            assertThat(historyBeforeTurn15).hasSize(20); // 10 user + 10 assistant items
            assertThat(historyBeforeTurn15.get(0).content()).isEqualTo("Question Turn 5");
            assertThat(historyBeforeTurn15.get(1).content()).isEqualTo("Answer for Question Turn 5");
            assertThat(historyBeforeTurn15.get(18).content()).isEqualTo("Question Turn 14");
            assertThat(historyBeforeTurn15.get(19).content()).isEqualTo("Answer for Question Turn 14");

            // Now send a 16th simulate request or turn to check final session history snapshot
            MessengerSimulateRequest simReq = new MessengerSimulateRequest(psid, "Check Final State", null);
            messengerService.simulateMessage(simReq);

            // Capture the 16th call
            ArgumentCaptor<CustomerChatRequest> simCaptor = ArgumentCaptor.forClass(CustomerChatRequest.class);
            verify(customerChatService, times(16)).chat(simCaptor.capture());

            CustomerChatRequest finalRequest = simCaptor.getAllValues().get(15);
            List<CustomerChatRequest.ConversationTurn> finalHistory = finalRequest.history();

            // Should have turns 6..15 (exactly 10 newest turns, 20 items)
            assertThat(finalHistory).hasSize(20);
            assertThat(finalHistory.get(0).content()).isEqualTo("Question Turn 6");
            assertThat(finalHistory.get(1).content()).isEqualTo("Answer for Question Turn 6");
            assertThat(finalHistory.get(18).content()).isEqualTo("Question Turn 15");
            assertThat(finalHistory.get(19).content()).isEqualTo("Answer for Question Turn 15");
        }

        @Test
        @DisplayName("Boundary: sessionMaxTurns=1 keeps only the single most recent turn")
        void sessionMaxTurnsOneKeepsOnlySingleTurn() {
            properties.setSessionMaxTurns(1);

            when(customerChatService.chat(any())).thenAnswer(invocation -> {
                CustomerChatRequest req = invocation.getArgument(0);
                return new CustomerChatResponse(
                        "Ans to " + req.resolvedQuestion(),
                        "Role", "GENERAL", "t", "m", List.of(), List.of(), "ix", 0, 0);
            });

            String psid = "psid-boundary-1";
            messengerService.handleWebhookPayload(null, createMessageWebhookJson(psid, "Turn A"));
            messengerService.handleWebhookPayload(null, createMessageWebhookJson(psid, "Turn B"));
            messengerService.handleWebhookPayload(null, createMessageWebhookJson(psid, "Turn C"));

            ArgumentCaptor<CustomerChatRequest> captor = ArgumentCaptor.forClass(CustomerChatRequest.class);
            verify(customerChatService, times(3)).chat(captor.capture());

            // Third request should only have history of Turn B (1 user + 1 assistant = 2 items)
            List<CustomerChatRequest.ConversationTurn> hist = captor.getAllValues().get(2).history();
            assertThat(hist).hasSize(2);
            assertThat(hist.get(0).content()).isEqualTo("Turn B");
            assertThat(hist.get(1).content()).isEqualTo("Ans to Turn B");
        }

        @Test
        @DisplayName("Stress: 100 turns into MessengerSession with maxTurns=10 does not leak memory or overflow bounds")
        void hundredTurnsStressDirectSession() {
            MessengerService.MessengerSession session = new MessengerService.MessengerSession("psid-100");
            for (int i = 1; i <= 100; i++) {
                session.addTurns("User msg " + i, "Assistant msg " + i, 10);
            }

            List<CustomerChatRequest.ConversationTurn> snapshot = session.getHistorySnapshot();
            assertThat(snapshot).hasSize(20);
            assertThat(snapshot.get(0).content()).isEqualTo("User msg 91");
            assertThat(snapshot.get(1).content()).isEqualTo("Assistant msg 91");
            assertThat(snapshot.get(18).content()).isEqualTo("User msg 100");
            assertThat(snapshot.get(19).content()).isEqualTo("Assistant msg 100");
        }
    }

    // =========================================================================
    // 2. LRU Cache Capacity & Eviction Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("2. LRU Session Cache Capacity & Eviction Tests")
    class LruCacheEvictionTests {

        @Test
        @DisplayName("Strict LRU eviction order: oldest accessed session is evicted when maxSessions reached")
        void strictLruEvictionOrderPreserved() {
            properties.setMaxSessions(3);

            when(customerChatService.chat(any())).thenAnswer(invocation -> {
                CustomerChatRequest req = invocation.getArgument(0);
                return new CustomerChatResponse(
                        "Reply " + req.resolvedQuestion(),
                        "Role", "GENERAL", "t", "m", List.of(), List.of(), "ix", 0, 0);
            });

            // 1. Create session A
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-A", "Msg A1"));
            // 2. Create session B
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-B", "Msg B1"));
            // 3. Create session C
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-C", "Msg C1"));

            assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(3);

            // 4. Touch session A again -> makes A most recently used, B is now the oldest!
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-A", "Msg A2"));

            // 5. Add session D -> Capacity is 3, so oldest (user-B) MUST be evicted!
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-D", "Msg D1"));

            assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(3);

            // Verify user-A still retained its history
            ArgumentCaptor<CustomerChatRequest> captor = ArgumentCaptor.forClass(CustomerChatRequest.class);
            // Touch user-A again to inspect history
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-A", "Msg A3"));

            verify(customerChatService, atLeastOnce()).chat(captor.capture());
            CustomerChatRequest lastReqForA = captor.getValue();
            assertThat(lastReqForA.resolvedQuestion()).isEqualTo("Msg A3");
            // History should contain previous turns of user-A
            assertThat(lastReqForA.history()).isNotEmpty();
            assertThat(lastReqForA.history().get(0).content()).isEqualTo("Msg A1");

            // Touch user-B (who was evicted) -> should now have EMPTY history because it's a new session!
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-B", "Msg B2"));
            verify(customerChatService, atLeastOnce()).chat(captor.capture());
            CustomerChatRequest reqForB = captor.getValue();
            assertThat(reqForB.resolvedQuestion()).isEqualTo("Msg B2");
            assertThat(reqForB.history()).isEmpty(); // Proves user-B was evicted and started fresh
        }

        @Test
        @DisplayName("High volume: creating 1,000 distinct sessions with maxSessions=50 strictly caps sessionMap at 50")
        void highVolumeSessionCreationStrictlyBounded() {
            properties.setMaxSessions(50);

            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("OK", "Role", "GENERAL", "t", "m", List.of(), List.of(), "ix", 0, 0));

            for (int i = 0; i < 1000; i++) {
                messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-vol-" + i, "Hello " + i));
            }

            MessengerStatusResponse status = messengerService.getStatus();
            assertThat(status.activeSessionsCount()).isEqualTo(50);
            assertThat(status.recentInteractions().size()).isLessThanOrEqualTo(properties.getMaxRecentInteractions());
        }

        @Test
        @DisplayName("Thread-safety under concurrent session creation and eviction")
        void concurrentSessionCreationAndEvictionUnderPressure() throws Exception {
            properties.setMaxSessions(25);

            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("Concurrent OK", "Role", "GENERAL", "t", "m", List.of(), List.of(), "ix", 0, 0));

            int threadCount = 10;
            int opsPerThread = 50;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(1);
            List<Throwable> exceptions = Collections.synchronizedList(new ArrayList<>());

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        latch.await();
                        for (int i = 0; i < opsPerThread; i++) {
                            String sender = "user-" + (threadId * 100 + i);
                            messengerService.handleWebhookPayload(null, createMessageWebhookJson(sender, "Concurrent msg " + i));
                        }
                    } catch (Throwable ex) {
                        exceptions.add(ex);
                    }
                });
            }

            latch.countDown();
            executor.shutdown();
            boolean finished = executor.awaitTermination(10, TimeUnit.SECONDS);

            assertThat(finished).isTrue();
            assertThat(exceptions).isEmpty();
            assertThat(messengerService.getStatus().activeSessionsCount()).isLessThanOrEqualTo(25);
        }
    }

    // =========================================================================
    // 3. Feedback Routing & Evolution Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("3. Feedback Routing & EvoAgentX Tests")
    class FeedbackRoutingTests {

        @Test
        @DisplayName("Postback FEEDBACK_HELPFUL_<interactionId> routes to recordFeedbackAndEvolve with rating=5 and helpful=true")
        void postbackHelpfulRoutesCorrectly() {
            CustomerEvolutionStatusResponse evoResp = new CustomerEvolutionStatusResponse(
                    true, "evo-engine", 1, 1, 1, 0.9, Map.of(), Map.of(), List.of());
            when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoResp);

            String postbackPayload = createPostbackWebhookJson("psid-helpful-user", "👍 Hài lòng", "FEEDBACK_HELPFUL_ix-sales-9999");
            boolean handled = messengerService.handleWebhookPayload(null, postbackPayload);

            assertThat(handled).isTrue();

            ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

            CustomerFeedbackRequest req = fbCaptor.getValue();
            assertThat(req.interactionId()).isEqualTo("ix-sales-9999");
            assertThat(req.helpful()).isTrue();
            assertThat(req.rating()).isEqualTo(5);
            assertThat(req.feedback()).contains("👍");

            // Verify ack sent via GraphClient
            verify(graphClient).sendTextMessage(eq("psid-helpful-user"), eq(properties.getThankYouFeedbackMessage()), eq(List.of()));
        }

        @Test
        @DisplayName("Postback FEEDBACK_UNHELPFUL_<interactionId> routes to recordFeedbackAndEvolve with rating=1 and helpful=false")
        void postbackUnhelpfulRoutesCorrectly() {
            CustomerEvolutionStatusResponse evoResp = new CustomerEvolutionStatusResponse(
                    true, "evo-engine", 1, 1, 1, 0.7, Map.of(), Map.of(), List.of());
            when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoResp);

            String postbackPayload = createPostbackWebhookJson("psid-unhelpful-user", "👎 Cần cải thiện", "FEEDBACK_UNHELPFUL_ix-warr-5555");
            boolean handled = messengerService.handleWebhookPayload(null, postbackPayload);

            assertThat(handled).isTrue();

            ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

            CustomerFeedbackRequest req = fbCaptor.getValue();
            assertThat(req.interactionId()).isEqualTo("ix-warr-5555");
            assertThat(req.helpful()).isFalse();
            assertThat(req.rating()).isEqualTo(1);
            assertThat(req.feedback()).contains("👎");

            // Verify unhelpful ack sent via GraphClient
            verify(graphClient).sendTextMessage(eq("psid-unhelpful-user"), eq(properties.getUnhelpfulFeedbackMessage()), eq(List.of()));
        }

        @Test
        @DisplayName("QuickReply payload FEEDBACK_HELPFUL_<interactionId> routes to recordFeedbackAndEvolve")
        void quickReplyFeedbackRoutesCorrectly() {
            CustomerEvolutionStatusResponse evoResp = new CustomerEvolutionStatusResponse(
                    true, "evo-engine", 1, 1, 1, 0.9, Map.of(), Map.of(), List.of());
            when(evolutionService.recordFeedbackAndEvolve(any())).thenReturn(evoResp);

            String qrPayload = createQuickReplyWebhookJson("psid-qr-fb", "FEEDBACK_HELPFUL_ix-qr-8888");
            boolean handled = messengerService.handleWebhookPayload(null, qrPayload);

            assertThat(handled).isTrue();

            ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

            CustomerFeedbackRequest req = fbCaptor.getValue();
            assertThat(req.interactionId()).isEqualTo("ix-qr-8888");
            assertThat(req.helpful()).isTrue();
            assertThat(req.rating()).isEqualTo(5);
        }

        @Test
        @DisplayName("Fallback to session lastInteractionId when payload lacks interactionId")
        void fallbackToSessionLastInteractionIdWhenPayloadEmpty() {
            // First send regular message to establish interactionId in session
            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("Ans", "Role", "SALES", "t", "m", List.of(), List.of(), "ix-session-prev-123", 1, 0));

            messengerService.handleWebhookPayload(null, createMessageWebhookJson("psid-prev", "Hello"));

            // Now send FEEDBACK_HELPFUL_ without interaction ID
            String feedbackPayload = createPostbackWebhookJson("psid-prev", "👍", "FEEDBACK_HELPFUL_");
            messengerService.handleWebhookPayload(null, feedbackPayload);

            ArgumentCaptor<CustomerFeedbackRequest> fbCaptor = ArgumentCaptor.forClass(CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());

            CustomerFeedbackRequest req = fbCaptor.getValue();
            assertThat(req.interactionId()).isEqualTo("ix-session-prev-123");
            assertThat(req.scenario()).isEqualTo("SALES");
        }

        @Test
        @DisplayName("Graceful resilience: exception in evolutionService does not crash webhook handler")
        void evolutionServiceExceptionHandledGracefully() {
            when(evolutionService.recordFeedbackAndEvolve(any()))
                    .thenThrow(new RuntimeException("Simulated Evolution DB error"));

            String postbackPayload = createPostbackWebhookJson("psid-resilient", "👍", "FEEDBACK_HELPFUL_ix-err-1");

            assertThatCode(() -> messengerService.handleWebhookPayload(null, postbackPayload))
                    .doesNotThrowAnyException();
        }
    }

    // =========================================================================
    // 4. Session Clearing Stress Tests
    // =========================================================================

    @Nested
    @DisplayName("4. Session Clearing Tests")
    class SessionClearingTests {

        @Test
        @DisplayName("Bulk clear: clearSessions(null) resets active sessions to 0 and purges recentInteractions")
        void bulkClearSessionsResetsAll() {
            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("Ans", "Role", "SALES", "t", "m", List.of(), List.of(), "ix", 1, 0));

            // Create 5 sessions
            for (int i = 1; i <= 5; i++) {
                messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-" + i, "Msg " + i));
            }

            assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(5);
            assertThat(messengerService.getStatus().recentInteractions()).hasSize(5);

            // Execute bulk clear
            MessengerSessionClearResponse clearResp = messengerService.clearSessions(null);

            assertThat(clearResp.clearedCount()).isEqualTo(5);
            assertThat(clearResp.message()).contains("cleared");

            // Verify state
            MessengerStatusResponse statusAfter = messengerService.getStatus();
            assertThat(statusAfter.activeSessionsCount()).isZero();
            assertThat(statusAfter.recentInteractions()).isEmpty();
        }

        @Test
        @DisplayName("Single clear: clearSessions(senderId) clears only target session, retaining others")
        void singleSessionClearSelectivelyClearsOnlyTarget() {
            when(customerChatService.chat(any())).thenReturn(
                    new CustomerChatResponse("Ans", "Role", "SALES", "t", "m", List.of(), List.of(), "ix", 1, 0));

            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-1", "Msg 1"));
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-2", "Msg 2"));
            messengerService.handleWebhookPayload(null, createMessageWebhookJson("user-3", "Msg 3"));

            assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(3);

            // Clear only user-2
            MessengerSessionClearResponse clearResp = messengerService.clearSessions("user-2");
            assertThat(clearResp.clearedCount()).isEqualTo(1);
            assertThat(clearResp.message()).contains("user-2");

            assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(2);

            // Clearing nonexistent session returns 0
            MessengerSessionClearResponse clearNonexistent = messengerService.clearSessions("user-nonexistent");
            assertThat(clearNonexistent.clearedCount()).isZero();
            assertThat(messengerService.getStatus().activeSessionsCount()).isEqualTo(2);
        }
    }

    // =========================================================================
    // Helper JSON generators
    // =========================================================================

    private static String createMessageWebhookJson(String senderId, String text) {
        return """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page-stress",
                      "time": %d,
                      "messaging": [
                        {
                          "sender": { "id": "%s" },
                          "recipient": { "id": "page-stress" },
                          "timestamp": %d,
                          "message": { "mid": "mid-%d", "text": "%s" }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(System.currentTimeMillis(), senderId, System.currentTimeMillis(), System.nanoTime(), text);
    }

    private static String createPostbackWebhookJson(String senderId, String title, String payload) {
        return """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page-stress",
                      "time": %d,
                      "messaging": [
                        {
                          "sender": { "id": "%s" },
                          "recipient": { "id": "page-stress" },
                          "timestamp": %d,
                          "postback": { "title": "%s", "payload": "%s" }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(System.currentTimeMillis(), senderId, System.currentTimeMillis(), title, payload);
    }

    private static String createQuickReplyWebhookJson(String senderId, String payload) {
        return """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page-stress",
                      "time": %d,
                      "messaging": [
                        {
                          "sender": { "id": "%s" },
                          "recipient": { "id": "page-stress" },
                          "timestamp": %d,
                          "message": {
                            "mid": "mid-%d",
                            "text": "User clicked QR",
                            "quick_reply": { "payload": "%s" }
                          }
                        }
                      ]
                    }
                  ]
                }
                """.formatted(System.currentTimeMillis(), senderId, System.currentTimeMillis(), System.nanoTime(), payload);
    }
}

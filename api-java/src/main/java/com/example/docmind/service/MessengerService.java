package com.example.docmind.service;

import com.example.docmind.config.MessengerProperties;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.dto.messenger.MessengerInteractionDto;
import com.example.docmind.dto.messenger.MessengerMessaging;
import com.example.docmind.dto.messenger.MessengerReaction;
import com.example.docmind.dto.messenger.MessengerSendRequest;
import com.example.docmind.dto.messenger.MessengerSessionClearResponse;
import com.example.docmind.dto.messenger.MessengerSimulateRequest;
import com.example.docmind.dto.messenger.MessengerSimulateResponse;
import com.example.docmind.dto.messenger.MessengerStatusResponse;
import com.example.docmind.dto.messenger.MessengerWebhookPayload;
import com.example.docmind.dto.messenger.SendResult;
import com.example.docmind.service.evolution.CustomerSelfEvolutionService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service điều phối tích hợp Facebook Messenger với hệ thống tư vấn khách hàng (CustomerChatService)
 * và động cơ tự tiến hoá EvoAgentX (CustomerSelfEvolutionService).
 */
@Service
public class MessengerService {

    private static final Logger log = LoggerFactory.getLogger(MessengerService.class);

    private final MessengerProperties properties;
    private final CustomerChatService customerChatService;
    private final CustomerSelfEvolutionService evolutionService;
    private final FacebookGraphClient facebookGraphClient;
    private final ObjectMapper objectMapper;

    private final Map<String, MessengerSession> sessionMap = new ConcurrentHashMap<>();
    private final Deque<MessengerInteractionDto> recentInteractions = new ConcurrentLinkedDeque<>();
    private final AtomicLong accessSequence = new AtomicLong();

    @Autowired
    public MessengerService(
            MessengerProperties properties,
            CustomerChatService customerChatService,
            CustomerSelfEvolutionService evolutionService,
            FacebookGraphClient facebookGraphClient) {
        this.properties = properties != null ? properties : new MessengerProperties();
        this.customerChatService = customerChatService;
        this.evolutionService = evolutionService;
        this.facebookGraphClient = facebookGraphClient;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Xác minh Webhook subscription challenge theo giao thức Meta.
     */
    public String verifyWebhook(String mode, String verifyToken, String challenge) {
        if (!"subscribe".equals(mode) || properties.getVerifyToken() == null
                || !properties.getVerifyToken().equals(verifyToken)) {
            log.warn("Messenger webhook verification failed: mode={}, token mismatch", mode);
            throw new SecurityException("Forbidden: Token verification failed");
        }
        log.info("Messenger webhook verification succeeded for challenge: {}", challenge);
        return challenge;
    }

    /**
     * Xác thực chữ ký HMAC-SHA256 trên header X-Hub-Signature-256 chống giả mạo payload và timing attack.
     */
    public boolean verifySignature(String firstArg, String secondArg) {
        if (!properties.isVerifySignature()) {
            return true;
        }
        String appSecret = properties.getAppSecret();
        if (appSecret == null || appSecret.isBlank()) {
            log.warn("verifySignature=true nhưng docmind.messenger.app-secret đang rỗng. Bỏ qua xác thực.");
            return true;
        }

        // Tự động nhận diện thứ tự tham số (signatureHeader vs rawPayload)
        String signatureHeader;
        String rawPayload;
        if (firstArg != null && firstArg.startsWith("sha256=")) {
            signatureHeader = firstArg;
            rawPayload = secondArg;
        } else if (secondArg != null && secondArg.startsWith("sha256=")) {
            signatureHeader = secondArg;
            rawPayload = firstArg;
        } else {
            signatureHeader = firstArg;
            rawPayload = secondArg;
        }

        if (signatureHeader == null || !signatureHeader.startsWith("sha256=")) {
            return false;
        }
        if (rawPayload == null) {
            return false;
        }

        String providedDigest = signatureHeader.substring("sha256=".length()).strip();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] computedHash = mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computedHash);

            return MessageDigest.isEqual(
                    computedHex.getBytes(StandardCharsets.UTF_8),
                    providedDigest.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            log.error("Lỗi khi tính toán HMAC-SHA256: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Tiếp nhận và phân phối sự kiện webhook từ Meta Platform.
     */
    public boolean handleWebhookPayload(String signatureHeader, String rawPayload) {
        if (properties.isVerifySignature()) {
            String appSecret = properties.getAppSecret();
            if (appSecret != null && !appSecret.isBlank()) {
                if (signatureHeader == null || signatureHeader.isBlank() || !verifySignature(signatureHeader, rawPayload)) {
                    log.warn("Messenger webhook signature verification failed: signature header is missing or invalid");
                    throw new SecurityException("Invalid HMAC-SHA256 signature");
                }
            } else {
                log.warn("verifySignature=true nhưng docmind.messenger.app-secret đang rỗng. Bỏ qua xác thực chữ ký.");
            }
        }

        if (rawPayload == null || rawPayload.isBlank()) {
            return true;
        }

        try {
            MessengerWebhookPayload payload = objectMapper.readValue(rawPayload, MessengerWebhookPayload.class);
            if (payload == null || payload.object() == null) {
                return true;
            }
            if (!"page".equalsIgnoreCase(payload.object())) {
                log.info("Non-page object received: {}. Ignoring gracefully.", payload.object());
                return true;
            }

            if (payload.entry() == null || payload.entry().isEmpty()) {
                return true;
            }

            for (var entry : payload.entry()) {
                if (entry == null || entry.messaging() == null) {
                    continue;
                }
                for (MessengerMessaging messaging : entry.messaging()) {
                    if (messaging == null) {
                        continue;
                    }
                    processMessagingEvent(messaging);
                }
            }
            return true;
        } catch (SecurityException se) {
            throw se;
        } catch (Exception ex) {
            log.error("Lỗi xử lý Messenger webhook payload: {}", ex.getMessage(), ex);
            return true; // Trả về true để Controller vẫn trả HTTP 200, tránh Meta Retry Storm
        }
    }

    /**
     * Xử lý từng sự kiện messaging đơn lẻ trong payload.
     */
    private void processMessagingEvent(MessengerMessaging messaging) {
        // 1. Bỏ qua tin nhắn delivery hoặc read receipt
        if (messaging.delivery() != null || messaging.read() != null) {
            log.debug("Acknowledge receipt delivery/read event");
            return;
        }

        // 2. Bỏ qua tin nhắn echo để chống lặp vô tận
        if (messaging.message() != null && Boolean.TRUE.equals(messaging.message().isEcho())) {
            log.debug("Ignoring echo message mid={}", messaging.message().mid());
            return;
        }

        String senderId = messaging.sender() != null ? messaging.sender().id() : null;
        if (senderId == null || senderId.isBlank()) {
            return;
        }

        // 3. Xử lý sự kiện Reaction
        if (messaging.reaction() != null) {
            handleReactionEvent(senderId, messaging.reaction());
            return;
        }

        // 4. Kiểm tra sự kiện Phản hồi Feedback (Quick Reply hoặc Postback)
        String qrPayload = messaging.message() != null && messaging.message().quickReply() != null
                ? messaging.message().quickReply().payload()
                : null;
        String pbPayload = messaging.postback() != null
                ? messaging.postback().payload()
                : null;

        String feedbackPayload = null;
        if (qrPayload != null && (qrPayload.startsWith("FEEDBACK_HELPFUL_") || qrPayload.startsWith("FEEDBACK_UNHELPFUL_"))) {
            feedbackPayload = qrPayload;
        } else if (pbPayload != null && (pbPayload.startsWith("FEEDBACK_HELPFUL_") || pbPayload.startsWith("FEEDBACK_UNHELPFUL_"))) {
            feedbackPayload = pbPayload;
        }

        if (feedbackPayload != null) {
            handleFeedbackEvent(senderId, feedbackPayload);
            return;
        }

        // 5. Xử lý tin nhắn hỏi đáp tư vấn khách hàng (Text hoặc câu hỏi từ Quick Reply/Postback)
        String userText = null;
        if (qrPayload != null && !qrPayload.isBlank()) {
            userText = qrPayload.strip();
        } else if (messaging.message() != null && messaging.message().text() != null && !messaging.message().text().isBlank()) {
            userText = messaging.message().text().strip();
        } else if (pbPayload != null && !pbPayload.isBlank()) {
            userText = pbPayload.strip();
        } else if (messaging.postback() != null && messaging.postback().title() != null && !messaging.postback().title().isBlank()) {
            userText = messaging.postback().title().strip();
        }

        if (userText == null || userText.isBlank()) {
            return;
        }

        handleCustomerTextMessage(senderId, userText);
    }

    /**
     * Xử lý tin nhắn text từ khách hàng, gọi CustomerChatService và phát hồi đáp qua FacebookGraphClient.
     */
    private void handleCustomerTextMessage(String senderId, String userText) {
        MessengerSession session = getOrCreateSession(senderId);
        List<CustomerChatRequest.ConversationTurn> historySnapshot = session.getHistorySnapshot();

        CustomerChatRequest chatRequest = new CustomerChatRequest(
                userText,
                null,
                null,
                properties.getDefaultCustomerName(),
                properties.getDefaultCustomerRole(),
                null, // Cho phép CustomerScenario tự động giải quyết theo ngữ cảnh
                null,
                null,
                historySnapshot,
                null
        );

        CustomerChatResponse response = customerChatService.chat(chatRequest);

        // Cập nhật session với câu trả lời mới
        session.addTurns(userText, response.answer(), properties.getSessionMaxTurns());
        session.setLastInteractionId(response.interactionId());
        session.setLastScenario(response.scenario());
        session.setLastActiveAt(Instant.now());

        // Chuẩn bị danh sách Quick Replies
        List<MessengerSendRequest.QuickReplyItem> quickReplies = new ArrayList<>();

        // Thêm câu hỏi gợi ý từ model
        if (response.suggestedQuestions() != null) {
            for (String sq : response.suggestedQuestions()) {
                if (sq != null && !sq.isBlank()) {
                    String title = truncateQuickReplyTitle(sq, properties.getMaxQuickReplyTitleChars());
                    quickReplies.add(MessengerSendRequest.QuickReplyItem.text(title, sq.strip()));
                }
            }
        }

        // Thêm 2 nút đánh giá hài lòng / chưa rõ nếu bật
        if (properties.isSendFeedbackQuickReplies()) {
            String interactionId = response.interactionId() != null ? response.interactionId() : "";
            String helpfulTitle = truncateQuickReplyTitle(properties.getHelpfulQuickReplyTitle(), properties.getMaxQuickReplyTitleChars());
            String unhelpfulTitle = truncateQuickReplyTitle(properties.getUnhelpfulQuickReplyTitle(), properties.getMaxQuickReplyTitleChars());

            quickReplies.add(MessengerSendRequest.QuickReplyItem.text(helpfulTitle, "FEEDBACK_HELPFUL_" + interactionId));
            quickReplies.add(MessengerSendRequest.QuickReplyItem.text(unhelpfulTitle, "FEEDBACK_UNHELPFUL_" + interactionId));
        }

        // Đảm bảo số lượng Quick Replies không vượt quá cấu hình
        if (quickReplies.size() > properties.getMaxQuickReplies()) {
            quickReplies = quickReplies.subList(0, properties.getMaxQuickReplies());
        }

        // Rút gọn text nếu vượt quá Meta limit
        String answerText = truncateMessageText(response.answer(), properties.getMaxMessageChars());

        // Phát tin nhắn qua Graph Client
        SendResult sendResult = facebookGraphClient.sendTextMessage(senderId, answerText, quickReplies);

        // Lưu vào lịch sử tương tác gần nhất
        MessengerInteractionDto interaction = new MessengerInteractionDto(
                response.interactionId(),
                senderId,
                userText,
                answerText,
                response.scenario(),
                sendResult != null ? sendResult.status() : "UNKNOWN",
                quickReplies.size(),
                Instant.now().toString(),
                sendResult != null && "SIMULATED".equalsIgnoreCase(sendResult.status())
        );
        recordInteraction(interaction);
    }

    /**
     * Tìm kiếm interactionId gần nhất của senderId từ bộ nhớ tạm recentInteractions làm phương án cứu cánh.
     */
    private String findRecentInteractionId(String senderId) {
        if (senderId == null || senderId.isBlank()) {
            return null;
        }
        for (MessengerInteractionDto item : recentInteractions) {
            if (senderId.equals(item.senderId()) && item.interactionId() != null && !item.interactionId().isBlank()) {
                return item.interactionId().strip();
            }
        }
        return null;
    }

    /**
     * Xử lý sự kiện phản hồi feedback từ nút bấm Quick Reply / Postback.
     * Đảm bảo an toàn null/blank interactionId, ghi log cảnh báo và luôn phản hồi khách hàng chu đáo.
     */
    private void handleFeedbackEvent(String senderId, String payload) {
        boolean isHelpful = payload.startsWith("FEEDBACK_HELPFUL_");
        String prefix = isHelpful ? "FEEDBACK_HELPFUL_" : "FEEDBACK_UNHELPFUL_";
        String rawId = payload.substring(prefix.length()).strip();

        MessengerSession session = sessionMap.get(senderId);
        String interactionId = !rawId.isBlank()
                ? rawId
                : (session != null && session.getLastInteractionId() != null && !session.getLastInteractionId().isBlank()
                        ? session.getLastInteractionId().strip()
                        : findRecentInteractionId(senderId));

        int rating = isHelpful ? 5 : 1;
        String comment = isHelpful
                ? "Khách hàng đánh giá hài lòng qua Facebook Messenger 👍"
                : "Khách hàng phản hồi cần cải thiện qua Facebook Messenger 👎";

        if (interactionId != null && !interactionId.isBlank()) {
            CustomerFeedbackRequest feedbackRequest = new CustomerFeedbackRequest(
                    interactionId.strip(),
                    null,
                    null,
                    session != null ? session.getLastScenario() : null,
                    rating,
                    isHelpful,
                    comment
            );

            if (evolutionService != null) {
                try {
                    evolutionService.recordFeedbackAndEvolve(feedbackRequest);
                } catch (Exception ex) {
                    log.warn("Lỗi khi ghi nhận feedback tiến hoá EvoAgentX cho senderId={}: {}", senderId, ex.getMessage(), ex);
                }
            }
        } else {
            log.warn("Tiếp nhận feedback từ senderId={} (helpful={}) nhưng interactionId bị null hoặc session đã hết hạn; bỏ qua cập nhật tiến hoá",
                    senderId, isHelpful);
        }

        // Luôn gửi tin nhắn cảm ơn / tiếp thu lại cho khách hàng dù interactionId có hay không
        try {
            String ackMessage = isHelpful
                    ? properties.getThankYouFeedbackMessage()
                    : properties.getUnhelpfulFeedbackMessage();

            facebookGraphClient.sendTextMessage(senderId, ackMessage, List.of());
        } catch (Exception ex) {
            log.warn("Không thể gửi tin nhắn cảm ơn feedback tới senderId={}: {}", senderId, ex.getMessage(), ex);
        }
    }

    /**
     * Xử lý sự kiện reaction trên tin nhắn Messenger.
     * Phòng thủ an toàn nếu interactionId bị null/trống khi session bị cắt tỉa hoặc khởi động lại.
     */
    private void handleReactionEvent(String senderId, MessengerReaction reaction) {
        if (reaction == null) {
            return;
        }

        String rType = reaction.reaction() != null ? reaction.reaction().toLowerCase(Locale.ROOT) : "";
        String emoji = reaction.emoji() != null ? reaction.emoji() : "";

        boolean isHelpful = "love".equals(rType) || "like".equals(rType) || "❤️".equals(emoji) || "👍".equals(emoji);
        int rating = isHelpful ? 5 : 1;
        String comment = "Messenger reaction: " + (reaction.reaction() != null ? reaction.reaction() : emoji);

        MessengerSession session = sessionMap.get(senderId);
        String interactionId = (session != null && session.getLastInteractionId() != null && !session.getLastInteractionId().isBlank())
                ? session.getLastInteractionId().strip()
                : findRecentInteractionId(senderId);

        if (interactionId != null && !interactionId.isBlank()) {
            CustomerFeedbackRequest feedbackRequest = new CustomerFeedbackRequest(
                    interactionId.strip(),
                    null,
                    null,
                    session != null ? session.getLastScenario() : null,
                    rating,
                    isHelpful,
                    comment
            );

            if (evolutionService != null) {
                try {
                    evolutionService.recordFeedbackAndEvolve(feedbackRequest);
                } catch (Exception ex) {
                    log.warn("Lỗi khi ghi nhận reaction tiến hoá EvoAgentX cho senderId={}: {}", senderId, ex.getMessage(), ex);
                }
            }
        } else {
            log.warn("Tiếp nhận reaction '{}' từ senderId={} nhưng không tìm thấy interactionId (session đã hết hạn hoặc chưa có tương tác); bỏ qua cập nhật tiến hoá",
                    reaction.reaction() != null ? reaction.reaction() : emoji, senderId);
        }
    }

    /**
     * Giả lập gửi tin nhắn Messenger end-to-end (dành cho Simulator & Dashboard offline).
     */
    public MessengerSimulateResponse simulateMessage(MessengerSimulateRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new IllegalArgumentException("message không được để trống");
        }

        String senderId = (request.senderId() != null && !request.senderId().isBlank())
                ? request.senderId().strip()
                : "sim-user-" + UUID.randomUUID().toString().substring(0, 8);

        MessengerSession session = getOrCreateSession(senderId);

        CustomerChatRequest chatReq = new CustomerChatRequest(
                request.message().strip(),
                null,
                null,
                properties.getDefaultCustomerName(),
                properties.getDefaultCustomerRole(),
                request.scenario(),
                null,
                null,
                session.getHistorySnapshot(),
                null
        );

        CustomerChatResponse chatResp = customerChatService.chat(chatReq);

        session.addTurns(request.message().strip(), chatResp.answer(), properties.getSessionMaxTurns());
        session.setLastInteractionId(chatResp.interactionId());
        session.setLastScenario(chatResp.scenario());
        session.setLastActiveAt(Instant.now());

        List<String> qrTitles = new ArrayList<>();
        if (chatResp.suggestedQuestions() != null) {
            for (String q : chatResp.suggestedQuestions()) {
                if (q != null && !q.isBlank()) {
                    qrTitles.add(truncateQuickReplyTitle(q, properties.getMaxQuickReplyTitleChars()));
                }
            }
        }
        if (properties.isSendFeedbackQuickReplies()) {
            qrTitles.add(truncateQuickReplyTitle(properties.getHelpfulQuickReplyTitle(), properties.getMaxQuickReplyTitleChars()));
            qrTitles.add(truncateQuickReplyTitle(properties.getUnhelpfulQuickReplyTitle(), properties.getMaxQuickReplyTitleChars()));
        }
        if (qrTitles.size() > properties.getMaxQuickReplies()) {
            qrTitles = qrTitles.subList(0, properties.getMaxQuickReplies());
        }

        MessengerInteractionDto interaction = new MessengerInteractionDto(
                chatResp.interactionId(),
                senderId,
                request.message().strip(),
                chatResp.answer(),
                chatResp.scenario(),
                "SIMULATED",
                qrTitles.size(),
                Instant.now().toString(),
                true
        );
        recordInteraction(interaction);

        return new MessengerSimulateResponse(
                senderId,
                request.message().strip(),
                chatResp.scenario(),
                chatResp.answer(),
                qrTitles,
                chatResp.interactionId(),
                true,
                Instant.now().toString()
        );
    }

    /**
     * Lấy trạng thái cấu hình và lịch sử tương tác gần nhất.
     */
    public MessengerStatusResponse getStatus() {
        boolean verifyTokenSet = properties.getVerifyToken() != null && !properties.getVerifyToken().isBlank();
        boolean pageTokenSet = properties.getPageAccessToken() != null && !properties.getPageAccessToken().isBlank();
        boolean appSecretSet = properties.getAppSecret() != null && !properties.getAppSecret().isBlank();

        List<MessengerInteractionDto> interactions = new ArrayList<>(recentInteractions);

        return new MessengerStatusResponse(
                properties.isEnabled(),
                verifyTokenSet,
                pageTokenSet,
                appSecretSet,
                properties.isVerifySignature(),
                sessionMap.size(),
                properties.getWebhookPath(),
                properties.getGraphBaseUrl(),
                properties.getApiVersion(),
                interactions
        );
    }

    /**
     * Xoá toàn bộ phiên hoặc phiên của một người dùng cụ thể.
     */
    public MessengerSessionClearResponse clearSessions(String senderId) {
        int count;
        String message;
        if (senderId != null && !senderId.isBlank()) {
            MessengerSession removed = sessionMap.remove(senderId.strip());
            count = removed != null ? 1 : 0;
            message = "Session for " + senderId.strip() + " cleared";
        } else {
            count = sessionMap.size();
            sessionMap.clear();
            recentInteractions.clear();
            message = "Messenger sessions cleared successfully";
        }
        return new MessengerSessionClearResponse(message, count, Instant.now().toString());
    }

    /**
     * Rút gọn tiêu đề Quick Reply đảm bảo ràng buộc cứng của Meta (tối đa 20 ký tự).
     */
    public static String truncateQuickReplyTitle(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "Hỏi tiếp";
        }
        String clean = text.strip();
        if (clean.length() <= maxChars) {
            return clean;
        }
        int cutoff = Math.max(1, maxChars - 3);
        return clean.substring(0, cutoff) + "...";
    }

    /**
     * Rút gọn tin nhắn nếu vượt quá trần ký tự Meta Graph API.
     */
    public static String truncateMessageText(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String clean = text.strip();
        if (clean.length() <= maxChars) {
            return clean;
        }
        int cutoff = Math.max(1, maxChars - 3);
        return clean.substring(0, cutoff) + "...";
    }

    private synchronized MessengerSession getOrCreateSession(String senderId) {
        MessengerSession session = sessionMap.get(senderId);
        if (session == null) {
            if (sessionMap.size() >= properties.getMaxSessions()) {
                evictOldestSession();
            }
            session = new MessengerSession(senderId);
            sessionMap.put(senderId, session);
        }
        session.setAccessOrder(accessSequence.incrementAndGet());
        session.setLastActiveAt(Instant.now());
        return session;
    }

    private synchronized void evictOldestSession() {
        String oldestKey = null;
        long minOrder = Long.MAX_VALUE;
        for (Map.Entry<String, MessengerSession> entry : sessionMap.entrySet()) {
            if (entry.getValue().getAccessOrder() < minOrder) {
                minOrder = entry.getValue().getAccessOrder();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) {
            sessionMap.remove(oldestKey);
            log.debug("LRU evicted oldest Messenger session: {}", oldestKey);
        }
    }

    private void recordInteraction(MessengerInteractionDto interaction) {
        recentInteractions.addFirst(interaction);
        while (recentInteractions.size() > properties.getMaxRecentInteractions()) {
            recentInteractions.removeLast();
        }
    }

    /**
     * Phiên lưu trữ trạng thái đa lượt của một người dùng Facebook (PSID).
     */
    public static class MessengerSession {
        private final String senderId;
        private final List<CustomerChatRequest.ConversationTurn> history = new ArrayList<>();
        private volatile String lastInteractionId;
        private volatile String lastScenario;
        private volatile Instant lastActiveAt = Instant.now();
        private volatile long accessOrder = 0;

        public MessengerSession(String senderId) {
            this.senderId = senderId;
        }

        public String getSenderId() {
            return senderId;
        }

        public synchronized void addTurns(String userText, String assistantText, int maxTurns) {
            if (userText != null && !userText.isBlank()) {
                history.add(new CustomerChatRequest.ConversationTurn("user", userText.strip()));
            }
            if (assistantText != null && !assistantText.isBlank()) {
                history.add(new CustomerChatRequest.ConversationTurn("assistant", assistantText.strip()));
            }
            int maxItems = Math.max(1, maxTurns) * 2;
            while (history.size() > maxItems) {
                history.remove(0);
            }
        }

        public synchronized List<CustomerChatRequest.ConversationTurn> getHistorySnapshot() {
            return new ArrayList<>(history);
        }

        public String getLastInteractionId() {
            return lastInteractionId;
        }

        public void setLastInteractionId(String lastInteractionId) {
            this.lastInteractionId = lastInteractionId;
        }

        public String getLastScenario() {
            return lastScenario;
        }

        public void setLastScenario(String lastScenario) {
            this.lastScenario = lastScenario;
        }

        public Instant getLastActiveAt() {
            return lastActiveAt;
        }

        public void setLastActiveAt(Instant lastActiveAt) {
            this.lastActiveAt = lastActiveAt;
        }

        public long getAccessOrder() {
            return accessOrder;
        }

        public void setAccessOrder(long accessOrder) {
            this.accessOrder = accessOrder;
        }
    }
}

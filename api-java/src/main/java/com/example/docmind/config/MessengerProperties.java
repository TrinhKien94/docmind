package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình toàn diện cho tính năng tích hợp Facebook Messenger (Customer Consultation & EvoAgentX).
 * Mọi thuộc tính đều được ngoại hoá ra application.properties và hỗ trợ biến môi trường.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.messenger")
public class MessengerProperties {

    /** Bật/tắt tính năng tích hợp Facebook Messenger. */
    private boolean enabled = true;

    /** Đường dẫn webhook nhận sự kiện từ Facebook. */
    private String webhookPath = "/api/messenger/webhook";

    /** Verify token dùng khi cấu hình Webhook trên Facebook App Dashboard. */
    private String verifyToken = "docmind-messenger-token";

    /** Page Access Token dùng để gọi Graph Send API. Nếu để trống -> chạy chế độ Giả lập (Simulation). */
    private String pageAccessToken = "";

    /** App Secret của Facebook App dùng để xác thực chữ ký X-Hub-Signature-256. */
    private String appSecret = "";

    /** Bật/tắt kiểm tra chữ ký HMAC-SHA256 trên webhook POST. */
    private boolean verifySignature = false;

    /** Base URL của Facebook Graph API. */
    private String graphBaseUrl = "https://graph.facebook.com";

    /** Phiên bản Graph API sử dụng. */
    private String apiVersion = "v21.0";

    /** Đường dẫn Send API (mặc định: /me/messages). */
    private String sendPath = "/me/messages";

    /** Thời gian chờ kết nối HTTP tới Graph API (ms). */
    private int connectTimeoutMs = 5000;

    /** Thời gian chờ đọc dữ liệu từ Graph API (ms). */
    private int readTimeoutMs = 10000;

    /** Độ dài ký tự tối đa của một tin nhắn gửi đi (Meta giới hạn 2000 ký tự). */
    private int maxMessageChars = 2000;

    /** Số lượng Quick Reply tối đa đính kèm dưới câu trả lời (Meta cho phép tối đa 13). */
    private int maxQuickReplies = 5;

    /** Giới hạn ký tự tối đa cho tiêu đề Quick Reply (Ràng buộc cứng từ Meta: tối đa 20 ký tự). */
    private int maxQuickReplyTitleChars = 20;

    /** Số lượt hội thoại gần nhất tối đa lưu trong phiên đa lượt của mỗi PSID. */
    private int sessionMaxTurns = 10;

    /** Số lượng phiên hội thoại tối đa lưu giữ trong bộ nhớ (cắt tỉa LRU khi vượt quá). */
    private int maxSessions = 500;

    /** Số lượng tương tác gần nhất lưu lại phục vụ Dashboard/Simulator. */
    private int maxRecentInteractions = 50;

    /** Vai trò khách hàng mặc định khi gửi tin qua Messenger. */
    private String defaultCustomerRole = "Khách hàng Facebook Messenger";

    /** Tên khách hàng mặc định khi gửi tin qua Messenger. */
    private String defaultCustomerName = "Khách hàng";

    /** Tự động đính kèm 2 nút Quick Reply đánh giá (👍 Hài lòng / 👎 Cần cải thiện). */
    private boolean sendFeedbackQuickReplies = true;

    /** Tiêu đề nút Quick Reply phản hồi hài lòng. */
    private String helpfulQuickReplyTitle = "👍 Hài lòng";

    /** Tiêu đề nút Quick Reply phản hồi chưa hài lòng. */
    private String unhelpfulQuickReplyTitle = "👎 Cần cải thiện";

    /** Tin nhắn cảm ơn khách hàng sau khi tiếp nhận đánh giá phản hồi. */
    private String thankYouFeedbackMessage =
            "Dạ DocMind xin cảm ơn góp ý của Anh/Chị! Hệ thống tự tiến hoá EvoAgentX đã tiếp thu để hoàn thiện hơn.";

    /** Tin nhắn phản hồi khi khách hàng đánh giá chưa hài lòng. */
    private String unhelpfulFeedbackMessage =
            "Dạ, DocMind xin lỗi vì chưa hỗ trợ trọn vẹn. Quý khách vui lòng đặt câu hỏi chi tiết hơn hoặc liên hệ Hotline: 1900-6868 nhé!";

    /** Alias getter/setter cho includeFeedbackQuickReplies. */
    public boolean isIncludeFeedbackQuickReplies() {
        return sendFeedbackQuickReplies;
    }

    public void setIncludeFeedbackQuickReplies(boolean include) {
        this.sendFeedbackQuickReplies = include;
    }

    public String getWebhookUrl() {
        return webhookPath;
    }

    public void setWebhookUrl(String webhookUrl) {
        this.webhookPath = webhookUrl;
    }
}

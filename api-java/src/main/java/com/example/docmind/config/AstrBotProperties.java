package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình trung tâm kết nối đa nền tảng chat & bảng điều khiển quản trị theo kiến trúc AstrBot
 * (https://github.com/astrbotdevs/astrbot), tích hợp trực tiếp với CustomerChatService (RAG + Dior KB)
 * và động cơ tự tiến hoá EvoAgentX (CustomerSelfEvolutionService).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "docmind.astrbot")
public class AstrBotProperties {

    /** Bật/tắt hệ thống đa nền tảng AstrBot. */
    private boolean enabled = true;

    /** Đường dẫn cơ sở cho Unified Webhook Callback của các nền tảng. */
    private String webhookBasePath = "/api/astrbot/webhooks/platforms";

    /** Hồ sơ cấu hình mặc định (Config Profile ID) khi phiên UMO chưa gắn rule riêng. */
    private String defaultFallbackProfileId = "dior_luxury_default";

    /** Persona mặc định cho các phiên tư vấn đa nền tảng. */
    private String defaultPersonaId = "dior_luxury_advisor";

    /** Kịch bản mặc định (AUTO, SALES, SERVICE_SUPPORT, WARRANTY_RETURN, ORDER_BILLING, GENERAL). */
    private String defaultScenario = "AUTO";

    /** Tiền tố lệnh quản trị nhanh (Star Command prefix, mặc định '/'). */
    private String commandPrefix = "/";

    /** Bật/tắt bộ lọc lệnh nội bộ (/help, /status, /reset, /persona, /scenario, /evolve, /kb). */
    private boolean enableStarCommands = true;

    /** Số lượng phiên UMO tối đa lưu giữ trong bộ nhớ (cắt tỉa LRU khi vượt ngưỡng). */
    private int maxSessions = 500;

    /** Số lượt hội thoại tối đa lưu trong lịch sử mỗi phiên UMO. */
    private int sessionMaxTurns = 12;

    /** Số lượng bản ghi Trace tối đa lưu trong bộ nhớ. */
    private int maxRecentTraces = 200;

    /** Số lượng dòng Console Log tối đa lưu trong bộ nhớ. */
    private int maxConsoleLogs = 300;

    /** Thời gian chờ kết nối HTTP khi gọi API các nền tảng bên ngoài (ms). */
    private int connectTimeoutMs = 5000;

    /** Thời gian chờ đọc phản hồi HTTP từ các nền tảng bên ngoài (ms). */
    private int readTimeoutMs = 10000;

    /** Tự động kích hoạt đánh giá TextGrad & MIPRO sau mỗi lượt chat đa nền tảng. */
    private boolean autoEvolveOnChat = true;

    /** Đính kèm gợi ý Quick Replies dưới câu trả lời đa nền tảng. */
    private boolean sendQuickReplies = true;

    /** Múi giờ hệ thống hiển thị trên Dashboard AstrBot. */
    private String timezone = "Asia/Ho_Chi_Minh";
}

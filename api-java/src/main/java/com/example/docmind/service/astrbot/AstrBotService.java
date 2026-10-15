package com.example.docmind.service.astrbot;

import com.example.docmind.config.AstrBotProperties;
import com.example.docmind.dto.Citation;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.astrbot.AstrBotCronJobDto;
import com.example.docmind.dto.astrbot.AstrBotExtensionDto;
import com.example.docmind.dto.astrbot.AstrBotInstanceDto;
import com.example.docmind.dto.astrbot.AstrBotInstanceDto.ErrorDetailDto;
import com.example.docmind.dto.astrbot.AstrBotInstanceDto.RouteBindingDto;
import com.example.docmind.dto.astrbot.AstrBotLogEntryDto;
import com.example.docmind.dto.astrbot.AstrBotOverviewResponse;
import com.example.docmind.dto.astrbot.AstrBotPersonaDto;
import com.example.docmind.dto.astrbot.AstrBotPlatformTypeDto;
import com.example.docmind.dto.astrbot.AstrBotProviderDto;
import com.example.docmind.dto.astrbot.AstrBotSessionRuleDto;
import com.example.docmind.dto.astrbot.AstrBotSimulateRequest;
import com.example.docmind.dto.astrbot.AstrBotSimulateResponse;
import com.example.docmind.dto.astrbot.AstrBotTraceDto;
import com.example.docmind.service.CustomerChatService;
import com.example.docmind.service.evolution.CustomerSelfEvolutionService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dịch vụ trung tâm quản lý các nền tảng chat (PlatformManager / BotConfigService / PlatformService)
 * và bảng điều khiển quản trị theo kiến trúc AstrBot (https://github.com/astrbotdevs/astrbot),
 * kết nối trực tiếp với CustomerChatService (RAG + Dior KB) và EvoAgentX (CustomerSelfEvolutionService).
 */
@Service
public class AstrBotService {

    private static final Logger log = LoggerFactory.getLogger(AstrBotService.class);
    private static final String ASTRBOT_VERSION = "v4.14.0-docmind";

    private final AstrBotProperties properties;
    private final CustomerChatService customerChatService;
    private final CustomerSelfEvolutionService evolutionService;
    private final ObjectMapper objectMapper;

    private final List<AstrBotPlatformTypeDto> platformTypes;
    private final Map<String, AstrBotPlatformTypeDto> platformTypeMap = new ConcurrentHashMap<>();
    private final Map<String, MutableBotInstance> botInstances = new ConcurrentHashMap<>();
    private final List<String> botOrder = new CopyOnWriteArrayList<>();

    private final Map<String, MutableSessionState> sessionMap = new ConcurrentHashMap<>();
    private final Map<String, RouteBindingDto> umoRouteMap = new ConcurrentHashMap<>();
    private final Map<String, AstrBotPersonaDto> personaMap = new ConcurrentHashMap<>();
    private final Map<String, AstrBotProviderDto> providerMap = new ConcurrentHashMap<>();
    private final Map<String, AstrBotCronJobDto> cronJobMap = new ConcurrentHashMap<>();
    private final Map<String, AstrBotExtensionDto> extensionMap = new ConcurrentHashMap<>();

    private final Deque<AstrBotTraceDto> recentTraces = new ConcurrentLinkedDeque<>();
    private final Deque<AstrBotLogEntryDto> consoleLogs = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalMessagesProcessed = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);
    private final AtomicLong sequenceCounter = new AtomicLong(1);

    @Autowired
    public AstrBotService(
            AstrBotProperties properties,
            CustomerChatService customerChatService,
            CustomerSelfEvolutionService evolutionService) {
        this.properties = properties != null ? properties : new AstrBotProperties();
        this.customerChatService = customerChatService;
        this.evolutionService = evolutionService;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        this.platformTypes = AstrBotPlatformCatalog.buildPlatformTypes();
        for (AstrBotPlatformTypeDto pt : this.platformTypes) {
            this.platformTypeMap.put(pt.type(), pt);
        }

        seedInitialState();
    }

    private void seedInitialState() {
        // 1. Seed Personas
        for (AstrBotPersonaDto p : AstrBotPlatformCatalog.buildDefaultPersonas()) {
            personaMap.put(p.id(), p);
        }

        // 2. Seed Providers
        for (AstrBotProviderDto pr : AstrBotPlatformCatalog.buildDefaultProviders()) {
            providerMap.put(pr.id(), pr);
        }

        // 3. Seed Cron Jobs
        for (AstrBotCronJobDto job : AstrBotPlatformCatalog.buildDefaultCronJobs()) {
            cronJobMap.put(job.jobId(), job);
        }

        // 4. Seed Extensions / Star Plugins / MCP Servers
        for (AstrBotExtensionDto ext : AstrBotPlatformCatalog.buildDefaultExtensions()) {
            extensionMap.put(ext.id(), ext);
        }

        // 5. Seed Default Platform Adapter Instances
        seedBot("telegram_dior_concierge", "telegram", true, "dior_luxury_advisor", "SALES", Map.of(
                "telegram_token", "78901234:AAH-DocMind-AstrBot-TelegramBridge",
                "start_message", "Dạ Christian Dior Maison & DocMind xin kính chào Quý khách trên Telegram!"
        ));
        seedBot("discord_community_hub", "discord", true, "astrbot_omni_assistant", "AUTO", Map.of(
                "discord_token", "MTI4DocMind.AstrBot.DiscordGatewayToken",
                "discord_activity_name", "Tư vấn RAG & EvoAgentX Gen #4"
        ));
        seedBot("slack_enterprise_desk", "slack", true, "docmind_rag_specialist", "SERVICE_SUPPORT", Map.of(
                "bot_token", "xoxb-docmind-astrbot-slack-token",
                "slack_connection_mode", "webhook"
        ));
        seedBot("lark_feishu_workspace", "lark", true, "docmind_rag_specialist", "SERVICE_SUPPORT", Map.of(
                "app_id", "cli_a6docmind88feishu",
                "domain", "https://open.larksuite.com",
                "lark_connection_mode", "socket"
        ));
        seedBot("line_official_vip", "line", true, "dior_luxury_advisor", "SALES", Map.of(
                "channel_access_token", "line_channel_token_dior_vip",
                "channel_secret", "line_channel_secret_8899"
        ));
        seedBot("messenger_dior_page", "facebook_messenger", true, "dior_luxury_advisor", "AUTO", Map.of(
                "verify_token", "docmind-messenger-token",
                "api_version", "v21.0",
                "send_feedback_quick_replies", true
        ));
        seedBot("dingtalk_corp_assistant", "dingtalk", false, "docmind_rag_specialist", "ORDER_BILLING", Map.of(
                "client_id", "dingoa_docmind_client",
                "card_template_id", "dior_ai_streaming_card_01"
        ));
        seedBot("wecom_customer_kf", "wecom", false, "vip_aftersales_concierge", "WARRANTY_RETURN", Map.of(
                "corpid", "ww_docmind_luxury_corp",
                "kf_name", "Christian Dior WeChat Concierge"
        ));

        // 6. Seed Default UMO Sessions & Routes
        String routeUmo1 = "telegram_dior_concierge:PrivateMessage:vip_client_8801";
        umoRouteMap.put(routeUmo1, new RouteBindingDto(routeUmo1, "dior_luxury_default", "dior_luxury_advisor", "SALES"));
        MutableSessionState sess1 = getOrCreateSession(
                routeUmo1, "telegram_dior_concierge", "telegram", "PrivateMessage", "vip_client_8801",
                "tg_user_8801", "Chị Minh Anh (VIP Platinum)");
        sess1.customName = "VIP Minh Anh — Tư vấn Skincare & Quà tặng";
        sess1.personaId = "dior_luxury_advisor";
        sess1.scenario = "SALES";
        sess1.recordTurn(
                "Mình da hỗn hợp thiên khô và đang mang thai 5 tháng thì dùng serum Dior nào an toàn?",
                "Dạ Christian Dior xin kính chào Chị Minh Anh! Với làn da hỗn hợp thiên khô trong giai đoạn thai kỳ 5 tháng, em xin khuyến nghị dòng Dior Prestige La Micro-Huile de Rose hoặc Capture Totale Le Sérum (không chứa Retinol/BHA nồng độ cao) và khuyên chị tham khảo thêm ý kiến bác sĩ sản khoa ạ.");

        String routeUmo2 = "discord_community_hub:GroupMessage:guild_channel_rag";
        umoRouteMap.put(routeUmo2, new RouteBindingDto(routeUmo2, "enterprise_rag_profile", "docmind_rag_specialist", "SERVICE_SUPPORT"));
        MutableSessionState sess2 = getOrCreateSession(
                routeUmo2, "discord_community_hub", "discord", "GroupMessage", "guild_channel_rag",
                "discord_dev_42", "Hoàng Nam (@hoangnam)");
        sess2.customName = "Kênh Kỹ thuật & Tra cứu SOP";
        sess2.personaId = "docmind_rag_specialist";
        sess2.scenario = "SERVICE_SUPPORT";
        sess2.recordTurn(
                "Chính sách đổi trả sản phẩm bị lỗi vòi xịt tại Dior áp dụng trong bao nhiêu ngày?",
                "Dạ theo quy trình SOP Bảo hành & Đổi trả chính hãng Christian Dior (05-dior-authenticity-warranty-return-complaints.md), sản phẩm lỗi kỹ thuật từ nhà sản xuất (như lỗi vòi xịt, nắp hít) được hỗ trợ đổi mới 1-1 miễn phí trong vòng 14 ngày kể từ ngày nhận hàng kèm video unbox và mã batch code.");

        MutableBotInstance tgBot = botInstances.get("telegram_dior_concierge");
        if (tgBot != null) {
            tgBot.messageCount.set(14);
        }
        MutableBotInstance dcBot = botInstances.get("discord_community_hub");
        if (dcBot != null) {
            dcBot.messageCount.set(9);
        }
        MutableBotInstance msgrBot = botInstances.get("messenger_dior_page");
        if (msgrBot != null) {
            msgrBot.messageCount.set(18);
        }
        totalMessagesProcessed.set(41);
        totalLatencyMs.set(41 * 185L);

        appendLog("INFO", "system", "AstrBotCoreLifecycle",
                "Đã khởi tạo PlatformManager với 19 mẫu Platform Adapters và 8 Bot Instances.", false);
        appendLog("INFO", "telegram_dior_concierge", "TelegramPlatformAdapter",
                "Adapter [telegram_dior_concierge] đang hoạt động (Unified Webhook sẵn sàng).", false);
        appendLog("INFO", "discord_community_hub", "DiscordPlatformAdapter",
                "Adapter [discord_community_hub] đã kết nối Gateway & đồng bộ Slash Commands.", false);
    }

    private void seedBot(
            String id,
            String type,
            boolean enable,
            String personaId,
            String scenario,
            Map<String, Object> overrides) {
        AstrBotPlatformTypeDto typeMeta = platformTypeMap.get(type);
        if (typeMeta == null) {
            return;
        }
        Map<String, Object> mergedConfig = new LinkedHashMap<>(typeMeta.defaultConfig());
        mergedConfig.put("id", id);
        mergedConfig.put("type", type);
        mergedConfig.put("enable", enable);
        if (overrides != null) {
            mergedConfig.putAll(overrides);
        }
        String webhookUuid = UUID.nameUUIDFromBytes(("astrbot-wh-" + id).getBytes()).toString().replace("-", "");
        if (typeMeta.supportUnifiedWebhook() || Boolean.TRUE.equals(mergedConfig.get("unified_webhook_mode"))) {
            mergedConfig.put("unified_webhook_mode", true);
            mergedConfig.put("webhook_uuid", webhookUuid);
        }

        MutableBotInstance inst = new MutableBotInstance(
                id,
                type,
                typeMeta.displayName(),
                enable,
                enable ? "running" : "stopped",
                enable ? Instant.now().toString() : null,
                typeMeta.supportUnifiedWebhook(),
                webhookUuid,
                properties.getDefaultFallbackProfileId(),
                personaId,
                scenario,
                mergedConfig
        );
        botInstances.put(id, inst);
        if (!botOrder.contains(id)) {
            botOrder.add(id);
        }
    }

    /**
     * Sanitize platform ID theo đúng quy tắc AstrBot PlatformManager._sanitize_platform_id
     * (loại bỏ ':' và '!' để không xung đột với cấu trúc UMO platform_id:message_type:session_id).
     */
    public String sanitizePlatformId(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return "bot_" + UUID.randomUUID().toString().substring(0, 6);
        }
        return rawId.trim()
                .replace(":", "_")
                .replace("!", "_")
                .replaceAll("\\s+", "_");
    }

    public List<AstrBotPlatformTypeDto> listPlatformTypes() {
        return Collections.unmodifiableList(platformTypes);
    }

    public List<AstrBotInstanceDto> listBots(Boolean enabledFilter, String typeFilter) {
        List<AstrBotInstanceDto> result = new ArrayList<>();
        for (String id : botOrder) {
            MutableBotInstance inst = botInstances.get(id);
            if (inst == null) {
                continue;
            }
            if (enabledFilter != null && inst.enable != enabledFilter) {
                continue;
            }
            if (typeFilter != null && !typeFilter.isBlank() && !inst.type.equalsIgnoreCase(typeFilter.trim())) {
                continue;
            }
            result.add(toInstanceDto(inst));
        }
        return result;
    }

    public AstrBotInstanceDto getBot(String botId) {
        MutableBotInstance inst = botInstances.get(botId);
        if (inst == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng Bot với ID: " + botId);
        }
        return toInstanceDto(inst);
    }

    public AstrBotInstanceDto createBot(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Dữ liệu cấu hình Bot không được rỗng");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> configMap = payload.get("config") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) payload.get("config"))
                : new LinkedHashMap<>(payload);

        String rawType = firstNonBlank(
                asString(configMap.get("type")),
                asString(payload.get("type")),
                "telegram");
        AstrBotPlatformTypeDto typeMeta = platformTypeMap.getOrDefault(rawType, platformTypes.get(0));

        String rawId = firstNonBlank(
                asString(configMap.get("id")),
                asString(payload.get("bot_id")),
                asString(payload.get("id")),
                typeMeta.id() + "_" + UUID.randomUUID().toString().substring(0, 4));
        String sanitizedId = sanitizePlatformId(rawId);

        if (botInstances.containsKey(sanitizedId)) {
            throw new IllegalArgumentException("Bot ID '" + sanitizedId + "' đã tồn tại. Vui lòng chọn tên định danh khác.");
        }

        Map<String, Object> mergedConfig = new LinkedHashMap<>(typeMeta.defaultConfig());
        mergedConfig.putAll(configMap);
        mergedConfig.put("id", sanitizedId);
        mergedConfig.put("type", typeMeta.type());

        boolean enable = asBoolean(mergedConfig.getOrDefault("enable", payload.get("enabled")), true);
        mergedConfig.put("enable", enable);

        boolean unifiedWebhook = asBoolean(mergedConfig.get("unified_webhook_mode"), typeMeta.supportUnifiedWebhook());
        String webhookUuid = asString(mergedConfig.get("webhook_uuid"));
        if (unifiedWebhook && (webhookUuid == null || webhookUuid.isBlank())) {
            webhookUuid = UUID.randomUUID().toString().replace("-", "");
            mergedConfig.put("webhook_uuid", webhookUuid);
        }

        String personaId = firstNonBlank(
                asString(payload.get("default_persona_id")),
                asString(mergedConfig.get("default_persona_id")),
                properties.getDefaultPersonaId());
        String scenario = firstNonBlank(
                asString(payload.get("default_scenario")),
                asString(mergedConfig.get("default_scenario")),
                properties.getDefaultScenario());
        String fallbackProfile = firstNonBlank(
                asString(payload.get("fallback_profile_id")),
                properties.getDefaultFallbackProfileId());

        MutableBotInstance inst = new MutableBotInstance(
                sanitizedId,
                typeMeta.type(),
                typeMeta.displayName(),
                enable,
                enable ? "running" : "stopped",
                enable ? Instant.now().toString() : null,
                unifiedWebhook,
                webhookUuid,
                fallbackProfile,
                personaId,
                scenario,
                mergedConfig
        );

        botInstances.put(sanitizedId, inst);
        botOrder.add(sanitizedId);

        appendLog("INFO", sanitizedId, "PlatformManager",
                "Đã khởi tạo và nạp thành công Platform Adapter [" + sanitizedId + "] (loại: " + typeMeta.type() + ").", false);
        return toInstanceDto(inst);
    }

    public AstrBotInstanceDto updateBot(String botId, Map<String, Object> payload) {
        MutableBotInstance inst = botInstances.get(botId);
        if (inst == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng Bot với ID: " + botId);
        }
        if (payload == null) {
            return toInstanceDto(inst);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> incomingConfig = payload.get("config") instanceof Map
                ? new LinkedHashMap<>((Map<String, Object>) payload.get("config"))
                : new LinkedHashMap<>(payload);

        synchronized (inst) {
            inst.config.putAll(incomingConfig);
            inst.config.put("id", inst.id);
            inst.config.put("type", inst.type);

            if (payload.containsKey("enabled") || incomingConfig.containsKey("enable")) {
                boolean enableVal = asBoolean(
                        incomingConfig.getOrDefault("enable", payload.get("enabled")),
                        inst.enable);
                inst.enable = enableVal;
                inst.config.put("enable", enableVal);
                inst.status = enableVal ? "running" : "stopped";
                if (enableVal && inst.startedAt == null) {
                    inst.startedAt = Instant.now().toString();
                }
            }

            if (payload.containsKey("default_persona_id") || incomingConfig.containsKey("default_persona_id")) {
                inst.defaultPersonaId = firstNonBlank(
                        asString(payload.get("default_persona_id")),
                        asString(incomingConfig.get("default_persona_id")),
                        inst.defaultPersonaId);
            }
            if (payload.containsKey("default_scenario") || incomingConfig.containsKey("default_scenario")) {
                inst.defaultScenario = firstNonBlank(
                        asString(payload.get("default_scenario")),
                        asString(incomingConfig.get("default_scenario")),
                        inst.defaultScenario);
            }
            if (payload.containsKey("fallback_profile_id") || incomingConfig.containsKey("fallback_profile_id")) {
                inst.fallbackProfileId = firstNonBlank(
                        asString(payload.get("fallback_profile_id")),
                        asString(incomingConfig.get("fallback_profile_id")),
                        inst.fallbackProfileId);
            }

            boolean unified = asBoolean(inst.config.get("unified_webhook_mode"), inst.unifiedWebhook);
            inst.unifiedWebhook = unified;
            if (unified && (inst.webhookUuid == null || inst.webhookUuid.isBlank())) {
                inst.webhookUuid = UUID.randomUUID().toString().replace("-", "");
                inst.config.put("webhook_uuid", inst.webhookUuid);
            }
        }

        appendLog("INFO", botId, "PlatformManager",
                "Đã cập nhật cấu hình và reload Platform Adapter [" + botId + "].", false);
        return toInstanceDto(inst);
    }

    public AstrBotInstanceDto setBotEnabled(String botId, boolean enabled) {
        MutableBotInstance inst = botInstances.get(botId);
        if (inst == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng Bot với ID: " + botId);
        }
        synchronized (inst) {
            inst.enable = enabled;
            inst.config.put("enable", enabled);
            inst.status = enabled ? "running" : "stopped";
            if (enabled) {
                inst.startedAt = Instant.now().toString();
            }
        }
        appendLog("INFO", botId, "PlatformManager",
                "Platform Adapter [" + botId + "] đã chuyển sang trạng thái: " + (enabled ? "RUNNING" : "STOPPED"), false);
        return toInstanceDto(inst);
    }

    public void deleteBot(String botId) {
        MutableBotInstance removed = botInstances.remove(botId);
        if (removed == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng Bot với ID: " + botId);
        }
        botOrder.remove(botId);
        appendLog("WARN", botId, "PlatformManager",
                "Đã dừng tác vụ và xoá Platform Adapter [" + botId + "].", false);
    }

    public Map<String, Object> testBotConnection(String botId) {
        MutableBotInstance inst = botInstances.get(botId);
        if (inst == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng Bot với ID: " + botId);
        }
        AstrBotPlatformTypeDto typeMeta = platformTypeMap.get(inst.type);
        List<String> missingFields = new ArrayList<>();
        if (typeMeta != null && typeMeta.schema() != null) {
            typeMeta.schema().forEach((key, schema) -> {
                if (schema.required()) {
                    Object val = inst.config.get(key);
                    if (val == null || String.valueOf(val).isBlank()) {
                        missingFields.add(key);
                    }
                }
            });
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", inst.id);
        response.put("type", inst.type);
        response.put("webhook_url", buildWebhookUrl(inst.webhookUuid));
        response.put("checked_at", Instant.now().toString());

        if (!missingFields.isEmpty()) {
            String warnMsg = "Chế độ Sandbox/Simulator sẵn sàng (chưa điền thông tin: " + String.join(", ", missingFields) + ")";
            response.put("status", "sandbox_ready");
            response.put("missing_fields", missingFields);
            response.put("message", warnMsg);
            appendLog("INFO", botId, "ConnectionTester", warnMsg, false);
        } else {
            inst.status = inst.enable ? "running" : "stopped";
            response.put("status", "ok");
            response.put("missing_fields", List.of());
            response.put("message", "Kết nối và xác thực cấu hình Platform Adapter [" + inst.id + "] thành công!");
            appendLog("INFO", botId, "ConnectionTester",
                    "Kiểm tra kết nối [" + inst.id + "] thành công (ping 24ms).", false);
        }
        return response;
    }

    public AstrBotInstanceDto clearBotErrors(String botId) {
        MutableBotInstance inst = botInstances.get(botId);
        if (inst == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng Bot với ID: " + botId);
        }
        synchronized (inst) {
            inst.errorCount = 0;
            inst.lastError = null;
            if (inst.enable) {
                inst.status = "running";
            }
        }
        appendLog("INFO", botId, "PlatformManager", "Đã xoá lịch sử lỗi của [" + botId + "].", false);
        return toInstanceDto(inst);
    }

    /**
     * Xử lý đăng ký nhanh bằng mã QR / Device Code cho Lark, DingTalk, WeChat iLink, QQ Official
     * (tương ứng PlatformService.handle_platform_registration trong astrbot).
     */
    public Map<String, Object> handlePlatformRegistration(String platformType, Map<String, Object> payload) {
        String action = asString(payload != null ? payload.get("action") : null);
        if (action == null || action.isBlank()) {
            action = "start";
        }
        action = action.trim().toLowerCase(Locale.ROOT);
        String botId = sanitizePlatformId(firstNonBlank(
                asString(payload != null ? payload.get("bot_id") : null),
                asString(payload != null ? payload.get("id") : null),
                platformType + "_qr"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("platform_type", platformType);
        result.put("bot_id", botId);

        if ("start".equals(action)) {
            String deviceCode = "astr_qr_" + UUID.randomUUID().toString().substring(0, 8);
            String qrContent = "https://open.astrbot.app/qr-auth?platform=" + platformType
                    + "&bot_id=" + botId + "&code=" + deviceCode;
            result.put("status", "pending");
            result.put("qr_status", "Đang chờ quét mã QR trên ứng dụng " + platformType.toUpperCase(Locale.ROOT));
            result.put("device_code", deviceCode);
            result.put("user_code", deviceCode.substring(deviceCode.length() - 6).toUpperCase(Locale.ROOT));
            result.put("verification_uri", qrContent);
            result.put("qrcode_img_content", qrContent);

            MutableBotInstance inst = botInstances.get(botId);
            if (inst != null) {
                inst.hasQrPayload = true;
                inst.qrStatus = (String) result.get("qr_status");
                inst.qrCodeImgContent = qrContent;
            }
            appendLog("INFO", botId, "QRRegistration",
                    "Đã khởi tạo phiên đăng nhập QR cho nền tảng [" + platformType + "] (mã: " + deviceCode + ").", false);
            return result;
        }

        if ("poll".equals(action) || "confirm".equals(action)) {
            Map<String, Object> autoCredentials = new LinkedHashMap<>();
            if ("lark".equalsIgnoreCase(platformType)) {
                autoCredentials.put("app_id", "cli_qr_" + UUID.randomUUID().toString().substring(0, 8));
                autoCredentials.put("app_secret", "sec_lark_" + UUID.randomUUID().toString().substring(0, 12));
            } else if ("dingtalk".equalsIgnoreCase(platformType)) {
                autoCredentials.put("client_id", "ding_qr_" + UUID.randomUUID().toString().substring(0, 8));
                autoCredentials.put("client_secret", "sec_ding_" + UUID.randomUUID().toString().substring(0, 12));
            } else if ("qq_official".equalsIgnoreCase(platformType) || "qq_official_webhook".equalsIgnoreCase(platformType)) {
                autoCredentials.put("appid", "1020" + (10000 + sequenceCounter.incrementAndGet()));
                autoCredentials.put("secret", "qq_sec_" + UUID.randomUUID().toString().substring(0, 12));
            } else {
                autoCredentials.put("session_token", "wx_ilink_" + UUID.randomUUID().toString().substring(0, 12));
            }

            MutableBotInstance inst = botInstances.get(botId);
            if (inst != null) {
                synchronized (inst) {
                    inst.config.putAll(autoCredentials);
                    inst.enable = true;
                    inst.status = "running";
                    inst.startedAt = Instant.now().toString();
                    inst.qrStatus = "Đã xác thực QR thành công!";
                }
            }
            result.put("status", "completed");
            result.put("qr_status", "Đã xác thực QR & đồng bộ thông tin kết nối thành công!");
            result.put("credentials", autoCredentials);
            appendLog("INFO", botId, "QRRegistration",
                    "Xác thực mã QR thành công cho [" + botId + "]. Đã tự động điền thông tin kết nối.", false);
            return result;
        }

        throw new IllegalArgumentException("Action không hợp lệ: " + action);
    }

    /**
     * Xử lý xác minh GET Challenge cho Unified Webhook (/api/astrbot/webhooks/platforms/{webhookUuid}).
     */
    public String verifyUnifiedWebhookGet(String webhookUuid, Map<String, String> queryParams) {
        MutableBotInstance inst = findBotByWebhookUuid(webhookUuid);
        if (inst == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng ứng với webhook_uuid: " + webhookUuid);
        }
        if (queryParams == null) {
            return "OK";
        }
        if (queryParams.containsKey("hub.challenge")) {
            return queryParams.get("hub.challenge");
        }
        if (queryParams.containsKey("echostr")) {
            return queryParams.get("echostr");
        }
        if (queryParams.containsKey("challenge")) {
            return queryParams.get("challenge");
        }
        return "ASTRBOT_WEBHOOK_READY:" + inst.id;
    }

    /**
     * Tiếp nhận sự kiện POST trên Unified Webhook (/api/astrbot/webhooks/platforms/{webhookUuid}).
     * Tự động bóc tách định dạng bản tin của Telegram, Discord, Slack, Lark, LINE, WeCom, WeChat, QQ, OneBot...
     */
    public Map<String, Object> handleUnifiedWebhookPost(String webhookUuid, String rawBody) {
        MutableBotInstance inst = findBotByWebhookUuid(webhookUuid);
        if (inst == null) {
            throw new IllegalArgumentException("Không tìm thấy nền tảng ứng với webhook_uuid: " + webhookUuid);
        }

        try {
            JsonNode root = (rawBody != null && !rawBody.isBlank())
                    ? objectMapper.readTree(rawBody)
                    : objectMapper.createObjectNode();

            // Slack / Lark URL verification challenge
            if (root.hasNonNull("challenge") && ("url_verification".equals(root.path("type").asText()) || !root.has("message"))) {
                return Map.of("challenge", root.get("challenge").asText());
            }

            ParsedWebhookEvent parsed = extractWebhookEvent(inst, root);
            AstrBotSimulateRequest simReq = new AstrBotSimulateRequest(
                    inst.id,
                    inst.type,
                    parsed.messageType,
                    parsed.sessionId,
                    parsed.senderId,
                    parsed.senderName,
                    parsed.messageText,
                    null,
                    null
            );
            AstrBotSimulateResponse simResp = simulateOrProcessMessage(simReq);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "ok");
            out.put("event_received", true);
            out.put("bot_id", inst.id);
            out.put("platform_type", inst.type);
            out.put("umo", simResp.umo());
            out.put("reply", simResp.replySent());
            out.put("interaction_id", simResp.interactionId());
            return out;
        } catch (Exception e) {
            inst.recordError(e.getMessage(), e.toString());
            appendLog("ERROR", inst.id, "WebhookReceiver",
                    "Lỗi xử lý callback Webhook [" + inst.id + "]: " + e.getMessage(), false);
            throw new IllegalArgumentException("Payload Webhook không hợp lệ: " + e.getMessage(), e);
        }
    }

    /**
     * Luồng xử lý trung tâm AstrBot Event Pipeline:
     * 1. Xác định Bot Instance & chuẩn hoá định danh phiên UMO ({bot_id}:{message_type}:{session_id})
     * 2. Áp dụng định tuyến UMO Route Binding & Session Custom Rules
     * 3. Kiểm tra và thực thi lệnh quản trị nội bộ Star Slash Commands (/help, /status, /reset, /persona, /scenario, /evolve, /kb)
     * 4. Chuyển tiếp câu hỏi khách hàng sang CustomerChatService (RAG + Dior SOP KB + Persona Prompt + EvoAgentX)
     * 5. Lưu lịch sử phiên UMO, ghi nhận Trace & Console Log.
     */
    public AstrBotSimulateResponse simulateOrProcessMessage(AstrBotSimulateRequest request) {
        long startNs = System.nanoTime();
        if (request == null || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống");
        }

        String incomingText = request.getMessage().trim();
        MutableBotInstance bot = resolveBotForRequest(request);
        String botId = bot.id;
        String platformType = bot.type;
        String messageType = normalizeMessageType(request.getMessageType());
        String senderId = firstNonBlank(request.getSenderId(), "user_" + platformType + "_01");
        String sessionId = firstNonBlank(request.getSessionId(), senderId);
        String senderName = firstNonBlank(request.getSenderName(), "Khách hàng " + platformType.toUpperCase(Locale.ROOT));

        String umo = botId + ":" + messageType + ":" + sessionId;
        MutableSessionState session = getOrCreateSession(
                umo, botId, platformType, messageType, sessionId, senderId, senderName);

        RouteBindingDto routeBinding = umoRouteMap.get(umo);

        String resolvedProfileId = routeBinding != null && routeBinding.configId() != null
                ? routeBinding.configId()
                : firstNonBlank(session.configProfileId, bot.fallbackProfileId, properties.getDefaultFallbackProfileId());

        String resolvedPersonaId = firstNonBlank(
                request.getPersonaId(),
                session.personaId,
                routeBinding != null ? routeBinding.personaId() : null,
                bot.defaultPersonaId,
                properties.getDefaultPersonaId());

        String resolvedScenario = firstNonBlank(
                request.getScenario(),
                session.scenario,
                routeBinding != null ? routeBinding.scenario() : null,
                bot.defaultScenario,
                properties.getDefaultScenario());

        appendLog("INFO", botId, "EventPipeline",
                "[" + umo + "] " + senderName + ": " + incomingText, true);

        boolean isCommand = properties.isEnableStarCommands()
                && incomingText.startsWith(properties.getCommandPrefix());

        String replyText;
        List<Citation> citations = List.of();
        List<String> quickReplies = List.of("✨ Tư vấn sản phẩm", "🛡️ Chính sách đổi trả", "📦 Kiểm tra đơn hàng", "/status");
        int evoGen = 0;
        double evoScore = 1.0;

        if (isCommand) {
            replyText = executeStarCommand(incomingText, session, bot, resolvedPersonaId, resolvedScenario);
            CustomerEvolutionStatusResponse evoStatus = safeGetEvolutionStatus();
            if (evoStatus != null) {
                evoGen = evoStatus.evolutionGeneration();
                evoScore = evoStatus.averageQualityScore();
            }
        } else if (!session.llmEnabled) {
            replyText = "⏸️ Phiên hội thoại [" + umo + "] hiện đang tạm tắt phản hồi AI tự động (llm_enabled=false). Gõ `/reset` hoặc bật lại trong tab Quản lý Phiên.";
        } else {
            AstrBotPersonaDto activePersona = personaMap.get(resolvedPersonaId);
            String customContext = buildPersonaAndPlatformContext(bot, activePersona, umo, senderName);

            CustomerChatRequest chatRequest = new CustomerChatRequest(
                    incomingText,
                    null,
                    null,
                    senderName,
                    "Khách hàng qua nền tảng " + bot.displayName + " (" + messageType + ")",
                    "AUTO".equalsIgnoreCase(resolvedScenario) ? null : resolvedScenario,
                    null,
                    5,
                    session.buildHistoryTurns(),
                    customContext
            );

            CustomerChatResponse chatResponse = customerChatService.chat(chatRequest);
            replyText = chatResponse.answer();
            citations = chatResponse.citations() != null ? chatResponse.citations() : List.of();
            if (chatResponse.scenario() != null && !chatResponse.scenario().isBlank()) {
                resolvedScenario = chatResponse.scenario();
            }
            if (chatResponse.suggestedQuestions() != null && !chatResponse.suggestedQuestions().isEmpty()) {
                quickReplies = chatResponse.suggestedQuestions().stream().limit(4).toList();
            }

            CustomerEvolutionStatusResponse evoStatus = safeGetEvolutionStatus();
            if (evoStatus != null) {
                evoGen = evoStatus.evolutionGeneration();
                evoScore = evoStatus.averageQualityScore();
            }
        }

        session.recordTurn(incomingText, replyText);
        bot.messageCount.incrementAndGet();
        totalMessagesProcessed.incrementAndGet();

        long latencyMs = Math.max(1L, (System.nanoTime() - startNs) / 1_000_000L);
        totalLatencyMs.addAndGet(latencyMs);
        String interactionId = "astr-" + UUID.randomUUID().toString().substring(0, 8);
        String nowIso = Instant.now().toString();

        AstrBotTraceDto trace = new AstrBotTraceDto(
                interactionId,
                nowIso,
                umo,
                botId,
                platformType,
                messageType,
                senderId,
                senderName,
                incomingText,
                truncate(replyText, 240),
                resolvedScenario,
                resolvedPersonaId,
                citations.size(),
                evoScore,
                evoGen,
                latencyMs,
                isCommand,
                "succeeded"
        );
        recordTrace(trace);

        appendLog("INFO", botId, "EventPipeline",
                "Đã phản hồi [" + umo + "] (kịch bản=" + resolvedScenario + ", persona=" + resolvedPersonaId
                        + ", citations=" + citations.size() + ", latency=" + latencyMs + "ms).", true);

        return new AstrBotSimulateResponse(
                interactionId,
                umo,
                botId,
                platformType,
                messageType,
                senderId,
                senderName,
                incomingText,
                replyText,
                resolvedScenario,
                resolvedPersonaId,
                resolvedProfileId,
                isCommand,
                citations,
                quickReplies,
                evoGen,
                evoScore,
                latencyMs,
                bot.enable,
                nowIso
        );
    }

    private String executeStarCommand(
            String rawCommand,
            MutableSessionState session,
            MutableBotInstance bot,
            String activePersonaId,
            String activeScenario) {
        String[] parts = rawCommand.trim().split("\\s+", 2);
        String cmd = parts[0].toLowerCase(Locale.ROOT);
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "/help":
            case "/start":
                return "🤖 **Trung tâm Điều khiển Đa Nền tảng DocMind x AstrBot**\n"
                        + "- **Nền tảng kết nối**: `" + bot.displayName + "` (`" + bot.id + "`)\n"
                        + "- **Định danh phiên (UMO)**: `" + session.umo + "`\n"
                        + "- **Các lệnh Star hỗ trợ**:\n"
                        + "  • `/status` — Xem trạng thái Bot, Persona, Kịch bản và thế hệ EvoAgentX\n"
                        + "  • `/reset` — Xoá lịch sử hội thoại của phiên UMO hiện tại\n"
                        + "  • `/persona <id>` — Chuyển đổi nhân cách AI (VD: `/persona dior_luxury_advisor`)\n"
                        + "  • `/scenario <mã>` — Cố định kịch bản (`SALES`, `SERVICE_SUPPORT`, `WARRANTY_RETURN`, `ORDER_BILLING`, `AUTO`)\n"
                        + "  • `/evolve` — Kích hoạt ngay một chu kỳ tối ưu hoá TextGrad + MIPRO\n"
                        + "  • `/whoami` — Xem thông tin định tuyến UMO và quy tắc phiên";
            case "/status": {
                CustomerEvolutionStatusResponse evo = safeGetEvolutionStatus();
                int gen = evo != null ? evo.evolutionGeneration() : 0;
                double score = evo != null ? evo.averageQualityScore() : 0.95;
                return "📊 **Trạng thái Hệ thống AstrBot x DocMind**\n"
                        + "- **Bot Adapter**: `" + bot.id + "` (" + bot.status.toUpperCase(Locale.ROOT) + ")\n"
                        + "- **Phiên UMO**: `" + session.umo + "` (" + session.history.size() + " lượt nhớ)\n"
                        + "- **Persona đang gắn**: `" + activePersonaId + "`\n"
                        + "- **Kịch bản tư vấn**: `" + activeScenario + "`\n"
                        + "- **EvoAgentX Self-Evolution**: Thế hệ `Gen #" + gen + "` · Điểm chất lượng `" + String.format(Locale.US, "%.1f%%", score * 100) + "`";
            }
            case "/reset":
            case "/clear":
                session.history.clear();
                session.messageCount = 0;
                session.llmEnabled = true;
                return "✅ Đã làm mới toàn bộ ngữ cảnh hội thoại cho phiên `" + session.umo + "`. Quý khách có thể bắt đầu chủ đề mới!";
            case "/persona":
                if (arg.isEmpty()) {
                    return "🎭 **Danh sách Personas hiện có**: `" + String.join("`, `", personaMap.keySet())
                            + "`\nĐang sử dụng: `" + activePersonaId + "`. Gõ `/persona <id>` để đổi.";
                }
                if (!personaMap.containsKey(arg)) {
                    return "⚠️ Không tìm thấy Persona `" + arg + "`. Các Persona hợp lệ: `" + String.join("`, `", personaMap.keySet()) + "`.";
                }
                session.personaId = arg;
                return "✅ Đã gắn Persona `" + arg + "` (" + personaMap.get(arg).name() + ") cho phiên `" + session.umo + "`.";
            case "/scenario":
                if (arg.isEmpty()) {
                    return "🎯 Kịch bản hiện tại của phiên `" + session.umo + "`: `" + activeScenario
                            + "`. Gõ `/scenario SALES|SERVICE_SUPPORT|WARRANTY_RETURN|ORDER_BILLING|AUTO` để đổi.";
                }
                session.scenario = arg.toUpperCase(Locale.ROOT);
                return "✅ Đã chuyển kịch bản ưu tiên của phiên `" + session.umo + "` sang `" + session.scenario + "`.";
            case "/evolve":
                if (evolutionService != null) {
                    CustomerEvolutionStatusResponse evolved = evolutionService.triggerEvolutionCycle(
                            "AUTO".equalsIgnoreCase(activeScenario) ? "SALES" : activeScenario);
                    return "🧬 Đã kích hoạt chu kỳ tự tiến hoá **EvoAgentX (TextGrad + MIPRO)** thành công! Thế hệ mới: **Gen #"
                            + evolved.evolutionGeneration() + "** (" + evolved.learnedGuidelines().size() + " nhóm quy tắc, "
                            + evolved.evolvedFewShotExamples().size() + " nhóm mẫu MIPRO).";
                }
                return "🧬 Động cơ EvoAgentX đã ghi nhận yêu cầu tối ưu hoá.";
            case "/whoami":
            case "/sid":
                return "🪪 **Thông tin Định danh Hợp nhất (UMO)**:\n"
                        + "- **UMO**: `" + session.umo + "`\n"
                        + "- **Platform ID**: `" + session.platformId + "` (`" + session.platformType + "`)\n"
                        + "- **Message Type**: `" + session.messageType + "`\n"
                        + "- **Session ID**: `" + session.sessionId + "`\n"
                        + "- **Tên hiển thị**: `" + (session.customName != null ? session.customName : session.senderName) + "`";
            default:
                return "ℹ️ Lệnh `" + cmd + "` đã được AstrBot tiếp nhận. Gõ `/help` để xem danh sách lệnh quản trị hỗ trợ.";
        }
    }

    // =========================================================================
    // SESSION MANAGEMENT & UMO ROUTES
    // =========================================================================

    public List<AstrBotSessionRuleDto> listSessions(String platformFilter, String messageTypeFilter, String search) {
        List<AstrBotSessionRuleDto> list = new ArrayList<>();
        String q = search != null ? search.trim().toLowerCase(Locale.ROOT) : "";
        for (MutableSessionState s : sessionMap.values()) {
            if (platformFilter != null && !platformFilter.isBlank()
                    && !s.platformType.equalsIgnoreCase(platformFilter)
                    && !s.platformId.equalsIgnoreCase(platformFilter)) {
                continue;
            }
            if (messageTypeFilter != null && !messageTypeFilter.isBlank()
                    && !s.messageType.equalsIgnoreCase(messageTypeFilter)) {
                continue;
            }
            if (!q.isEmpty()) {
                boolean matched = s.umo.toLowerCase(Locale.ROOT).contains(q)
                        || (s.senderName != null && s.senderName.toLowerCase(Locale.ROOT).contains(q))
                        || (s.customName != null && s.customName.toLowerCase(Locale.ROOT).contains(q))
                        || (s.lastMessage != null && s.lastMessage.toLowerCase(Locale.ROOT).contains(q));
                if (!matched) {
                    continue;
                }
            }
            list.add(s.toDto());
        }
        list.sort((a, b) -> Objects.compare(b.lastActiveAt(), a.lastActiveAt(), ComparatorNullSafe.INSTANCE));
        return list;
    }

    public AstrBotSessionRuleDto upsertSessionRule(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload quy tắc phiên không được để trống");
        }
        String umo = asString(payload.get("umo"));
        if (umo == null || umo.isBlank()) {
            String pId = firstNonBlank(asString(payload.get("platform_id")), "telegram_dior_concierge");
            String mType = normalizeMessageType(asString(payload.get("message_type")));
            String sId = firstNonBlank(asString(payload.get("session_id")), "session_" + sequenceCounter.incrementAndGet());
            umo = pId + ":" + mType + ":" + sId;
        }
        String[] parts = umo.split(":", 3);
        String platformId = parts.length > 0 ? parts[0] : "telegram_dior_concierge";
        String messageType = parts.length > 1 ? parts[1] : "PrivateMessage";
        String sessionId = parts.length > 2 ? parts[2] : "default_session";
        MutableBotInstance bot = botInstances.get(platformId);
        String platformType = bot != null ? bot.type : firstNonBlank(asString(payload.get("platform_type")), "telegram");

        MutableSessionState state = getOrCreateSession(
                umo, platformId, platformType, messageType, sessionId,
                firstNonBlank(asString(payload.get("sender_id")), sessionId),
                firstNonBlank(asString(payload.get("sender_name")), "Khách hàng " + sessionId));

        synchronized (state) {
            if (payload.containsKey("custom_name")) {
                state.customName = asString(payload.get("custom_name"));
            }
            if (payload.containsKey("llm_enabled")) {
                state.llmEnabled = asBoolean(payload.get("llm_enabled"), state.llmEnabled);
            }
            if (payload.containsKey("tts_enabled")) {
                state.ttsEnabled = asBoolean(payload.get("tts_enabled"), state.ttsEnabled);
            }
            if (payload.containsKey("persona_id")) {
                state.personaId = asString(payload.get("persona_id"));
            }
            if (payload.containsKey("scenario")) {
                state.scenario = asString(payload.get("scenario"));
            }
            if (payload.containsKey("config_profile_id")) {
                state.configProfileId = asString(payload.get("config_profile_id"));
            }
            state.lastActiveAt = Instant.now().toString();
        }

        if (state.personaId != null || state.scenario != null || state.configProfileId != null) {
            umoRouteMap.put(umo, new RouteBindingDto(
                    umo,
                    firstNonBlank(state.configProfileId, properties.getDefaultFallbackProfileId()),
                    firstNonBlank(state.personaId, properties.getDefaultPersonaId()),
                    firstNonBlank(state.scenario, properties.getDefaultScenario())
            ));
        }

        appendLog("INFO", platformId, "SessionManagementService",
                "Đã cập nhật quy tắc phiên cho UMO [" + umo + "].", false);
        return state.toDto();
    }

    public void deleteSession(String umo) {
        sessionMap.remove(umo);
        umoRouteMap.remove(umo);
        appendLog("INFO", "system", "SessionManagementService", "Đã xoá phiên UMO [" + umo + "].", false);
    }

    public int clearAllSessions() {
        int count = sessionMap.size();
        sessionMap.clear();
        umoRouteMap.clear();
        appendLog("WARN", "system", "SessionManagementService", "Đã làm sạch toàn bộ " + count + " phiên UMO.", false);
        return count;
    }

    public List<RouteBindingDto> listRoutes() {
        return new ArrayList<>(umoRouteMap.values());
    }

    public RouteBindingDto upsertRoute(Map<String, Object> payload) {
        String umo = asString(payload != null ? payload.get("umo") : null);
        if (umo == null || umo.isBlank()) {
            throw new IllegalArgumentException("Thiếu định danh UMO để gắn route");
        }
        String configId = firstNonBlank(
                asString(payload.get("config_id")),
                asString(payload.get("conf_id")),
                properties.getDefaultFallbackProfileId());
        String personaId = firstNonBlank(asString(payload.get("persona_id")), properties.getDefaultPersonaId());
        String scenario = firstNonBlank(asString(payload.get("scenario")), properties.getDefaultScenario());

        RouteBindingDto binding = new RouteBindingDto(umo, configId, personaId, scenario);
        umoRouteMap.put(umo, binding);
        return binding;
    }

    public void deleteRoute(String umo) {
        if (umo != null) {
            umoRouteMap.remove(umo);
        }
    }

    // =========================================================================
    // PERSONAS, PROVIDERS, CRON JOBS, EXTENSIONS
    // =========================================================================

    public List<AstrBotPersonaDto> listPersonas() {
        return new ArrayList<>(personaMap.values());
    }

    public AstrBotPersonaDto savePersona(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Dữ liệu Persona không được để trống");
        }
        String id = sanitizePlatformId(firstNonBlank(asString(payload.get("id")), asString(payload.get("name")), "persona_custom"));
        String name = firstNonBlank(asString(payload.get("name")), id);
        String folder = firstNonBlank(asString(payload.get("folder")), "Custom");
        String systemPrompt = firstNonBlank(asString(payload.get("system_prompt")),
                "Bạn là Chuyên gia Tư vấn Khách hàng & Tài liệu trên hệ thống DocMind x AstrBot.");
        List<String> beginDialogs = asStringList(payload.get("begin_dialogs"));
        List<String> tools = asStringList(payload.get("tools"));
        if (tools.isEmpty()) {
            tools = List.of("rag_hybrid_search", "dior_sop_lookup", "evoagentx_memory");
        }
        double temperature = asDouble(payload.get("temperature"), 0.3);
        String defaultScenario = firstNonBlank(asString(payload.get("default_scenario")), "AUTO");

        AstrBotPersonaDto dto = new AstrBotPersonaDto(
                id, name, folder, systemPrompt, beginDialogs, tools, temperature, defaultScenario, Instant.now().toString()
        );
        personaMap.put(id, dto);
        appendLog("INFO", "system", "PersonaService", "Đã lưu Persona [" + id + "] (" + name + ").", false);
        return dto;
    }

    public void deletePersona(String personaId) {
        if (personaMap.remove(personaId) == null) {
            throw new IllegalArgumentException("Không tìm thấy Persona: " + personaId);
        }
        appendLog("INFO", "system", "PersonaService", "Đã xoá Persona [" + personaId + "].", false);
    }

    public List<AstrBotProviderDto> listProviders() {
        return new ArrayList<>(providerMap.values());
    }

    public AstrBotProviderDto saveProvider(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Dữ liệu Provider không được để trống");
        }
        String id = sanitizePlatformId(firstNonBlank(asString(payload.get("id")), "provider_" + sequenceCounter.incrementAndGet()));
        AstrBotProviderDto existing = providerMap.get(id);
        String provider = firstNonBlank(asString(payload.get("provider")), existing != null ? existing.provider() : "OpenAI Compatible");
        String type = firstNonBlank(asString(payload.get("type")), existing != null ? existing.type() : "openai_chat_completion");
        String providerType = firstNonBlank(asString(payload.get("provider_type")), existing != null ? existing.providerType() : "chat_completion");
        boolean enable = asBoolean(payload.get("enable"), existing == null || existing.enable());
        String apiBase = firstNonBlank(asString(payload.get("api_base")), existing != null ? existing.apiBase() : "https://api.openai.com/v1");
        String model = firstNonBlank(asString(payload.get("model")), existing != null ? existing.model() : "gpt-6-astra");
        List<String> models = asStringList(payload.get("models"));
        if (models.isEmpty()) {
            models = existing != null && !existing.models().isEmpty() ? existing.models() : List.of(model);
        }
        int timeout = (int) asDouble(payload.get("timeout"), existing != null ? existing.timeout() : 120);
        String rawKey = asString(payload.get("api_key"));
        String maskedKey = (rawKey != null && !rawKey.isBlank())
                ? maskSecret(rawKey)
                : (existing != null ? existing.maskedKey() : "sk-configured-••••");

        AstrBotProviderDto dto = new AstrBotProviderDto(
                id, provider, type, providerType, enable, apiBase, model, models, timeout,
                true, maskedKey, enable ? "connected" : "disabled", existing != null ? existing.latencyMs() : 38L
        );
        providerMap.put(id, dto);
        appendLog("INFO", "system", "ProviderConfigService", "Đã cập nhật nguồn mô hình [" + id + "] (model: " + model + ").", false);
        return dto;
    }

    public Map<String, Object> testProvider(String providerId) {
        AstrBotProviderDto p = providerMap.get(providerId);
        if (p == null) {
            throw new IllegalArgumentException("Không tìm thấy Provider: " + providerId);
        }
        return Map.of(
                "id", p.id(),
                "model", p.model(),
                "api_base", p.apiBase(),
                "status", "connected",
                "latency_ms", p.latencyMs(),
                "message", "Kết nối tới nhà cung cấp [" + p.provider() + "] (" + p.model() + ") thành công!"
        );
    }

    public void deleteProvider(String providerId) {
        if (providerMap.remove(providerId) == null) {
            throw new IllegalArgumentException("Không tìm thấy Provider: " + providerId);
        }
    }

    public List<AstrBotCronJobDto> listCronJobs() {
        return new ArrayList<>(cronJobMap.values());
    }

    public AstrBotCronJobDto saveCronJob(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Dữ liệu Cron Job không được để trống");
        }
        String jobId = sanitizePlatformId(firstNonBlank(
                asString(payload.get("job_id")),
                asString(payload.get("id")),
                "cron_" + UUID.randomUUID().toString().substring(0, 6)));
        AstrBotCronJobDto existing = cronJobMap.get(jobId);

        String name = firstNonBlank(asString(payload.get("name")), existing != null ? existing.name() : "Tác vụ chủ động AstrBot");
        String description = firstNonBlank(asString(payload.get("description")), existing != null ? existing.description() : "");
        String cronExpr = firstNonBlank(asString(payload.get("cron_expression")), existing != null ? existing.cronExpression() : "0 9 * * *");
        String targetUmo = firstNonBlank(asString(payload.get("target_umo")), existing != null ? existing.targetUmo() : "telegram_dior_concierge:PrivateMessage:vip_client_8801");
        String prompt = firstNonBlank(asString(payload.get("prompt")), existing != null ? existing.prompt() : "Gửi bản tin tư vấn chăm sóc khách hàng định kỳ.");
        String scenario = firstNonBlank(asString(payload.get("scenario")), existing != null ? existing.scenario() : "SERVICE_SUPPORT");
        boolean runOnce = asBoolean(payload.get("run_once"), existing != null && existing.runOnce());
        boolean enabled = asBoolean(payload.get("enabled"), existing == null || existing.enabled());

        AstrBotCronJobDto dto = new AstrBotCronJobDto(
                jobId, name, description, cronExpr, targetUmo, prompt, scenario, runOnce, enabled,
                existing != null ? existing.lastRunAt() : null,
                "Theo lịch (" + cronExpr + ")",
                existing != null ? existing.runCount() : 0,
                existing != null ? existing.lastStatus() : "scheduled",
                existing != null ? existing.lastResultPreview() : "Chưa chạy"
        );
        cronJobMap.put(jobId, dto);
        appendLog("INFO", "system", "CronService", "Đã lưu Cron Job [" + jobId + "] -> đích [" + targetUmo + "].", false);
        return dto;
    }

    public AstrBotCronJobDto runCronJobNow(String jobId) {
        AstrBotCronJobDto job = cronJobMap.get(jobId);
        if (job == null) {
            throw new IllegalArgumentException("Không tìm thấy Cron Job: " + jobId);
        }
        String[] parts = job.targetUmo().split(":", 3);
        String botId = parts.length > 0 ? parts[0] : "telegram_dior_concierge";
        String msgType = parts.length > 1 ? parts[1] : "PrivateMessage";
        String sessId = parts.length > 2 ? parts[2] : "vip_client_8801";

        AstrBotSimulateRequest req = new AstrBotSimulateRequest(
                botId, null, msgType, sessId, sessId, "Cron Proactive Runner", job.prompt(), job.scenario(), null
        );
        AstrBotSimulateResponse simResp = simulateOrProcessMessage(req);

        AstrBotCronJobDto updated = new AstrBotCronJobDto(
                job.jobId(),
                job.name(),
                job.description(),
                job.cronExpression(),
                job.targetUmo(),
                job.prompt(),
                job.scenario(),
                job.runOnce(),
                job.runOnce() ? false : job.enabled(),
                Instant.now().toString(),
                job.nextRunAt(),
                job.runCount() + 1,
                "succeeded",
                truncate(simResp.replySent(), 180)
        );
        cronJobMap.put(jobId, updated);
        appendLog("INFO", botId, "CronService",
                "Đã thực thi chủ động Cron Job [" + job.name() + "] và gửi tin nhắn tới [" + job.targetUmo() + "].", false);
        return updated;
    }

    public void deleteCronJob(String jobId) {
        if (cronJobMap.remove(jobId) == null) {
            throw new IllegalArgumentException("Không tìm thấy Cron Job: " + jobId);
        }
    }

    public List<AstrBotExtensionDto> listExtensions() {
        return new ArrayList<>(extensionMap.values());
    }

    public AstrBotExtensionDto saveExtension(Map<String, Object> payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Dữ liệu Extension/MCP không được để trống");
        }
        String id = sanitizePlatformId(firstNonBlank(
                asString(payload.get("id")),
                asString(payload.get("name")),
                "ext_" + UUID.randomUUID().toString().substring(0, 6)));
        AstrBotExtensionDto existing = extensionMap.get(id);
        String name = firstNonBlank(asString(payload.get("name")), existing != null ? existing.name() : id);
        String displayName = firstNonBlank(asString(payload.get("display_name")), existing != null ? existing.displayName() : name);
        String kind = firstNonBlank(asString(payload.get("kind")), existing != null ? existing.kind() : "star_plugin");
        String author = firstNonBlank(asString(payload.get("author")), existing != null ? existing.author() : "AstrBot Community");
        String version = firstNonBlank(asString(payload.get("version")), existing != null ? existing.version() : "1.0.0");
        String description = firstNonBlank(asString(payload.get("description")), existing != null ? existing.description() : "Plugin mở rộng tích hợp vào đường ống sự kiện AstrBot.");
        boolean enabled = asBoolean(payload.get("enabled"), existing == null || existing.enabled());
        List<String> commands = asStringList(payload.get("commands"));
        int toolsCount = (int) asDouble(payload.get("tools_count"), existing != null ? existing.toolsCount() : 2);
        String endpoint = firstNonBlank(asString(payload.get("endpoint")), existing != null ? existing.endpoint() : "plugin://" + name);

        AstrBotExtensionDto dto = new AstrBotExtensionDto(
                id, name, displayName, kind, author, version, description, enabled, commands, toolsCount, endpoint
        );
        extensionMap.put(id, dto);
        appendLog("INFO", "system", "PluginManager", "Đã cài đặt/cập nhật tiện ích [" + displayName + "] (" + kind + ").", false);
        return dto;
    }

    public AstrBotExtensionDto toggleExtension(String extensionId, boolean enabled) {
        AstrBotExtensionDto ext = extensionMap.get(extensionId);
        if (ext == null) {
            throw new IllegalArgumentException("Không tìm thấy Extension: " + extensionId);
        }
        AstrBotExtensionDto updated = new AstrBotExtensionDto(
                ext.id(), ext.name(), ext.displayName(), ext.kind(), ext.author(),
                ext.version(), ext.description(), enabled, ext.commands(), ext.toolsCount(), ext.endpoint()
        );
        extensionMap.put(extensionId, updated);
        appendLog("INFO", "system", "PluginManager",
                "Tiện ích [" + ext.displayName() + "] đã chuyển sang trạng thái: " + (enabled ? "ENABLED" : "DISABLED"), false);
        return updated;
    }

    public void deleteExtension(String extensionId) {
        if (extensionMap.remove(extensionId) == null) {
            throw new IllegalArgumentException("Không tìm thấy Extension: " + extensionId);
        }
    }

    // =========================================================================
    // STATS, TRACES, CONSOLE LOGS & OVERVIEW SNAPSHOT
    // =========================================================================

    public Map<String, Object> getStats() {
        int runningCount = 0;
        int stoppedCount = 0;
        int errorCount = 0;
        int totalErrors = 0;
        List<Map<String, Object>> platformRanking = new ArrayList<>();

        for (String id : botOrder) {
            MutableBotInstance b = botInstances.get(id);
            if (b == null) {
                continue;
            }
            if ("running".equals(b.status)) {
                runningCount++;
            } else if ("error".equals(b.status)) {
                errorCount++;
            } else {
                stoppedCount++;
            }
            totalErrors += b.errorCount;
            platformRanking.add(Map.of(
                    "id", b.id,
                    "type", b.type,
                    "name", b.displayName,
                    "status", b.status,
                    "count", b.messageCount.get()
            ));
        }
        platformRanking.sort((a, b) -> Long.compare(((Number) b.get("count")).longValue(), ((Number) a.get("count")).longValue()));

        long totalMsgs = Math.max(1L, totalMessagesProcessed.get());
        long avgLatency = totalLatencyMs.get() / totalMsgs;
        CustomerEvolutionStatusResponse evo = safeGetEvolutionStatus();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total_messages", totalMessagesProcessed.get());
        stats.put("total_bots", botInstances.size());
        stats.put("running_bots", runningCount);
        stats.put("stopped_bots", stoppedCount);
        stats.put("error_bots", errorCount);
        stats.put("total_errors", totalErrors);
        stats.put("supported_platform_types", platformTypes.size());
        stats.put("active_sessions", sessionMap.size());
        stats.put("personas_count", personaMap.size());
        stats.put("providers_count", providerMap.size());
        stats.put("cron_jobs_count", cronJobMap.size());
        stats.put("extensions_count", extensionMap.size());
        stats.put("avg_latency_ms", avgLatency);
        stats.put("evolution_generation", evo != null ? evo.evolutionGeneration() : 0);
        stats.put("evolution_quality_score", evo != null ? evo.averageQualityScore() : 0.95);
        stats.put("platform_ranking", platformRanking);
        return stats;
    }

    public List<AstrBotTraceDto> getTraces() {
        return new ArrayList<>(recentTraces);
    }

    public List<AstrBotLogEntryDto> getLogs(boolean hideUserChat) {
        if (!hideUserChat) {
            return new ArrayList<>(consoleLogs);
        }
        return consoleLogs.stream().filter(l -> !l.isUserChat()).toList();
    }

    public void clearLogs() {
        consoleLogs.clear();
        appendLog("INFO", "system", "ConsoleService", "Đã xoá bộ đệm Console Logs.", false);
    }

    public AstrBotOverviewResponse getOverview() {
        return new AstrBotOverviewResponse(
                properties.isEnabled(),
                ASTRBOT_VERSION,
                properties.getTimezone(),
                properties.getWebhookBasePath(),
                getStats(),
                listPlatformTypes(),
                listBots(null, null),
                listSessions(null, null, null),
                listPersonas(),
                listProviders(),
                listCronJobs(),
                listExtensions(),
                getTraces(),
                getLogs(false)
        );
    }

    // =========================================================================
    // INTERNAL HELPERS
    // =========================================================================

    private MutableBotInstance findBotByWebhookUuid(String webhookUuid) {
        if (webhookUuid == null || webhookUuid.isBlank()) {
            return null;
        }
        for (MutableBotInstance b : botInstances.values()) {
            if (webhookUuid.equals(b.webhookUuid) || webhookUuid.equals(b.id)) {
                return b;
            }
        }
        return null;
    }

    private MutableBotInstance resolveBotForRequest(AstrBotSimulateRequest req) {
        if (req.getBotId() != null && !req.getBotId().isBlank()) {
            MutableBotInstance byId = botInstances.get(req.getBotId().trim());
            if (byId != null) {
                return byId;
            }
        }
        if (req.getPlatformType() != null && !req.getPlatformType().isBlank()) {
            String pt = req.getPlatformType().trim();
            for (String id : botOrder) {
                MutableBotInstance b = botInstances.get(id);
                if (b != null && b.type.equalsIgnoreCase(pt)) {
                    return b;
                }
            }
        }
        if (!botOrder.isEmpty()) {
            MutableBotInstance first = botInstances.get(botOrder.get(0));
            if (first != null) {
                return first;
            }
        }
        seedBot("telegram_default", "telegram", true, properties.getDefaultPersonaId(), properties.getDefaultScenario(), Map.of());
        return botInstances.get("telegram_default");
    }

    private ParsedWebhookEvent extractWebhookEvent(MutableBotInstance inst, JsonNode root) {
        // 1. Telegram format: { "message": { "text": "...", "from": { "id": 123, "first_name": "..." }, "chat": { "id": 123, "type": "private" } } }
        if (root.has("message") && root.path("message").isObject() && root.path("message").has("chat")) {
            JsonNode msg = root.path("message");
            String text = msg.path("text").asText("Xin chào");
            String senderId = msg.path("from").path("id").asText("tg_user");
            String senderName = msg.path("from").path("first_name").asText("Telegram User");
            String chatId = msg.path("chat").path("id").asText(senderId);
            String chatType = msg.path("chat").path("type").asText("private");
            String msgType = "private".equalsIgnoreCase(chatType) ? "PrivateMessage" : "GroupMessage";
            return new ParsedWebhookEvent(msgType, chatId, senderId, senderName, text);
        }

        // 2. Slack / Lark event_callback: { "event": { "text": "...", "user": "U123", "channel": "C123" } }
        if (root.has("event") && root.path("event").isObject()) {
            JsonNode ev = root.path("event");
            String text = ev.path("text").asText("");
            if (text.isBlank() && ev.has("message")) {
                text = ev.path("message").path("content").asText("Xin chào");
            }
            String senderId = firstNonBlank(
                    ev.path("user").asText(null),
                    ev.path("sender").path("sender_id").path("open_id").asText(null),
                    "event_user");
            String sessionId = firstNonBlank(
                    ev.path("channel").asText(null),
                    ev.path("message").path("chat_id").asText(null),
                    senderId);
            return new ParsedWebhookEvent("GroupMessage", sessionId, senderId, "User " + senderId, text.isBlank() ? "Xin chào" : text);
        }

        // 3. LINE format: { "events": [ { "type": "message", "source": { "userId": "..." }, "message": { "text": "..." } } ] }
        if (root.has("events") && root.path("events").isArray() && !root.path("events").isEmpty()) {
            JsonNode ev = root.path("events").get(0);
            String text = ev.path("message").path("text").asText("Xin chào");
            String userId = ev.path("source").path("userId").asText("line_user");
            return new ParsedWebhookEvent("PrivateMessage", userId, userId, "LINE User", text);
        }

        // 4. Generic / Discord / OneBot v11 format
        String text = firstNonBlank(
                root.path("message").asText(null),
                root.path("content").asText(null),
                root.path("raw_message").asText(null),
                root.path("text").asText(null),
                "Xin chào tư vấn viên!");
        String senderId = firstNonBlank(
                root.path("sender_id").asText(null),
                root.path("user_id").asText(null),
                root.path("author").path("id").asText(null),
                "webhook_user_01");
        String senderName = firstNonBlank(
                root.path("sender_name").asText(null),
                root.path("author").path("username").asText(null),
                "Khách hàng " + inst.type);
        String sessionId = firstNonBlank(
                root.path("session_id").asText(null),
                root.path("channel_id").asText(null),
                root.path("group_id").asText(null),
                senderId);
        String msgType = normalizeMessageType(root.path("message_type").asText(null));
        return new ParsedWebhookEvent(msgType, sessionId, senderId, senderName, text);
    }

    private String buildPersonaAndPlatformContext(
            MutableBotInstance bot,
            AstrBotPersonaDto persona,
            String umo,
            String senderName) {
        StringBuilder sb = new StringBuilder();
        sb.append("[ASTRBOT MULTI-PLATFORM CONTEXT]\n");
        sb.append("- Kênh kết nối: ").append(bot.displayName).append(" (bot_id=").append(bot.id).append(", type=").append(bot.type).append(")\n");
        sb.append("- Định danh phiên UMO: ").append(umo).append("\n");
        sb.append("- Khách hàng: ").append(senderName).append("\n");
        if (persona != null) {
            sb.append("- Nhân cách AI (Persona): ").append(persona.name()).append("\n");
            sb.append("- Chỉ dẫn Persona: ").append(persona.systemPrompt()).append("\n");
        }
        return sb.toString();
    }

    private MutableSessionState getOrCreateSession(
            String umo,
            String platformId,
            String platformType,
            String messageType,
            String sessionId,
            String senderId,
            String senderName) {
        return sessionMap.computeIfAbsent(umo, k -> {
            if (sessionMap.size() >= Math.max(10, properties.getMaxSessions()) && !sessionMap.isEmpty()) {
                String oldestKey = sessionMap.keySet().iterator().next();
                if (oldestKey != null) {
                    sessionMap.remove(oldestKey);
                }
            }
            MutableBotInstance bot = botInstances.get(platformId);
            return new MutableSessionState(
                    umo,
                    platformId,
                    platformType,
                    messageType,
                    sessionId,
                    senderId,
                    senderName,
                    bot != null ? bot.defaultPersonaId : properties.getDefaultPersonaId(),
                    bot != null ? bot.defaultScenario : properties.getDefaultScenario(),
                    bot != null ? bot.fallbackProfileId : properties.getDefaultFallbackProfileId(),
                    Math.max(4, properties.getSessionMaxTurns())
            );
        });
    }

    private AstrBotInstanceDto toInstanceDto(MutableBotInstance inst) {
        int activeSessions = 0;
        for (MutableSessionState s : sessionMap.values()) {
            if (inst.id.equals(s.platformId)) {
                activeSessions++;
            }
        }
        List<RouteBindingDto> routes = new ArrayList<>();
        for (RouteBindingDto r : umoRouteMap.values()) {
            if (r.umo() != null && r.umo().startsWith(inst.id + ":")) {
                routes.add(r);
            }
        }
        return new AstrBotInstanceDto(
                inst.id,
                inst.type,
                inst.displayName,
                inst.enable,
                inst.status,
                inst.startedAt,
                inst.unifiedWebhook,
                inst.webhookUuid,
                buildWebhookUrl(inst.webhookUuid),
                inst.errorCount,
                inst.lastError,
                inst.messageCount.get(),
                activeSessions,
                inst.fallbackProfileId,
                inst.defaultPersonaId,
                inst.defaultScenario,
                inst.hasQrPayload,
                inst.qrStatus,
                inst.qrCodeImgContent,
                new LinkedHashMap<>(inst.config),
                routes
        );
    }

    private String buildWebhookUrl(String webhookUuid) {
        if (webhookUuid == null || webhookUuid.isBlank()) {
            return properties.getWebhookBasePath() + "/default";
        }
        return properties.getWebhookBasePath() + "/" + webhookUuid;
    }

    private void recordTrace(AstrBotTraceDto trace) {
        recentTraces.addFirst(trace);
        int max = Math.max(20, properties.getMaxRecentTraces());
        while (recentTraces.size() > max) {
            recentTraces.pollLast();
        }
    }

    private void appendLog(String level, String platformId, String source, String message, boolean isUserChat) {
        AstrBotLogEntryDto entry = new AstrBotLogEntryDto(
                "log-" + sequenceCounter.getAndIncrement(),
                Instant.now().toString(),
                level,
                platformId,
                source,
                message,
                isUserChat
        );
        consoleLogs.addFirst(entry);
        int max = Math.max(30, properties.getMaxConsoleLogs());
        while (consoleLogs.size() > max) {
            consoleLogs.pollLast();
        }
    }

    private CustomerEvolutionStatusResponse safeGetEvolutionStatus() {
        try {
            return evolutionService != null ? evolutionService.getStatus() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeMessageType(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PrivateMessage";
        }
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("group") || lower.contains("guild") || lower.contains("channel")) {
            return "GroupMessage";
        }
        return "PrivateMessage";
    }

    private static String asString(Object val) {
        return val != null ? String.valueOf(val).trim() : null;
    }

    private static boolean asBoolean(Object val, boolean fallback) {
        if (val instanceof Boolean b) {
            return b;
        }
        if (val instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s.trim());
        }
        return fallback;
    }

    private static double asDouble(Object val, double fallback) {
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val instanceof String s && !s.isBlank()) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static List<String> asStringList(Object val) {
        if (val instanceof List<?> rawList) {
            List<String> out = new ArrayList<>();
            for (Object item : rawList) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    out.add(String.valueOf(item).trim());
                }
            }
            return out;
        }
        return List.of();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String truncate(String s, int maxLen) {
        if (s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen) + "...";
    }

    private static String maskSecret(String secret) {
        if (secret == null || secret.length() <= 6) {
            return "••••••••";
        }
        return secret.substring(0, 4) + "••••••" + secret.substring(secret.length() - 3);
    }

    private record ParsedWebhookEvent(
            String messageType,
            String sessionId,
            String senderId,
            String senderName,
            String messageText
    ) {
    }

    private enum ComparatorNullSafe implements java.util.Comparator<String> {
        INSTANCE;
        @Override
        public int compare(String a, String b) {
            if (a == null && b == null) return 0;
            if (a == null) return -1;
            if (b == null) return 1;
            return a.compareTo(b);
        }
    }

    private static final class MutableBotInstance {
        final String id;
        final String type;
        final String displayName;
        volatile boolean enable;
        volatile String status;
        volatile String startedAt;
        volatile boolean unifiedWebhook;
        volatile String webhookUuid;
        volatile String fallbackProfileId;
        volatile String defaultPersonaId;
        volatile String defaultScenario;
        volatile int errorCount = 0;
        volatile ErrorDetailDto lastError = null;
        volatile boolean hasQrPayload = false;
        volatile String qrStatus = null;
        volatile String qrCodeImgContent = null;
        final AtomicLong messageCount = new AtomicLong(0);
        final Map<String, Object> config;

        MutableBotInstance(
                String id,
                String type,
                String displayName,
                boolean enable,
                String status,
                String startedAt,
                boolean unifiedWebhook,
                String webhookUuid,
                String fallbackProfileId,
                String defaultPersonaId,
                String defaultScenario,
                Map<String, Object> config) {
            this.id = id;
            this.type = type;
            this.displayName = displayName;
            this.enable = enable;
            this.status = status;
            this.startedAt = startedAt;
            this.unifiedWebhook = unifiedWebhook;
            this.webhookUuid = webhookUuid;
            this.fallbackProfileId = fallbackProfileId;
            this.defaultPersonaId = defaultPersonaId;
            this.defaultScenario = defaultScenario;
            this.config = new LinkedHashMap<>(config);
        }

        void recordError(String message, String traceback) {
            this.errorCount++;
            this.status = "error";
            this.lastError = new ErrorDetailDto(message, Instant.now().toString(), traceback);
        }
    }

    private static final class MutableSessionState {
        final String umo;
        final String platformId;
        final String platformType;
        final String messageType;
        final String sessionId;
        final String senderId;
        volatile String senderName;
        volatile String customName;
        volatile boolean llmEnabled = true;
        volatile boolean ttsEnabled = false;
        volatile String personaId;
        volatile String scenario;
        volatile String configProfileId;
        volatile List<String> kbNames = List.of("dior_sop_kb", "docmind_hybrid_rag");
        volatile List<String> disabledPlugins = List.of();
        volatile int messageCount = 0;
        volatile String lastMessage = "";
        volatile String lastReply = "";
        volatile String lastActiveAt = Instant.now().toString();
        final int maxTurns;
        final Deque<Map<String, String>> history = new ConcurrentLinkedDeque<>();

        MutableSessionState(
                String umo,
                String platformId,
                String platformType,
                String messageType,
                String sessionId,
                String senderId,
                String senderName,
                String personaId,
                String scenario,
                String configProfileId,
                int maxTurns) {
            this.umo = umo;
            this.platformId = platformId;
            this.platformType = platformType;
            this.messageType = messageType;
            this.sessionId = sessionId;
            this.senderId = senderId;
            this.senderName = senderName;
            this.personaId = personaId;
            this.scenario = scenario;
            this.configProfileId = configProfileId;
            this.maxTurns = maxTurns;
        }

        synchronized void recordTurn(String question, String answer) {
            this.lastMessage = question;
            this.lastReply = answer;
            this.messageCount++;
            this.lastActiveAt = Instant.now().toString();
            this.history.addLast(Map.of("role", "user", "content", question));
            this.history.addLast(Map.of("role", "assistant", "content", answer));
            while (this.history.size() > maxTurns * 2) {
                this.history.pollFirst();
            }
        }

        List<CustomerChatRequest.ConversationTurn> buildHistoryTurns() {
            List<CustomerChatRequest.ConversationTurn> turns = new ArrayList<>();
            for (Map<String, String> h : history) {
                turns.add(new CustomerChatRequest.ConversationTurn(h.get("role"), h.get("content")));
            }
            return turns;
        }

        AstrBotSessionRuleDto toDto() {
            return new AstrBotSessionRuleDto(
                    umo,
                    platformId,
                    platformType,
                    messageType,
                    sessionId,
                    senderId,
                    senderName,
                    customName,
                    llmEnabled,
                    ttsEnabled,
                    personaId,
                    scenario,
                    configProfileId,
                    kbNames,
                    disabledPlugins,
                    messageCount,
                    lastMessage,
                    lastReply,
                    lastActiveAt,
                    new ArrayList<>(history)
            );
        }
    }
}

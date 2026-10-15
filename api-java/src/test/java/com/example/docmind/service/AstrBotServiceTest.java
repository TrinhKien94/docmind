package com.example.docmind.service;

import com.example.docmind.config.AstrBotProperties;
import com.example.docmind.dto.Citation;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.astrbot.AstrBotCronJobDto;
import com.example.docmind.dto.astrbot.AstrBotExtensionDto;
import com.example.docmind.dto.astrbot.AstrBotInstanceDto;
import com.example.docmind.dto.astrbot.AstrBotOverviewResponse;
import com.example.docmind.dto.astrbot.AstrBotPersonaDto;
import com.example.docmind.dto.astrbot.AstrBotPlatformTypeDto;
import com.example.docmind.dto.astrbot.AstrBotProviderDto;
import com.example.docmind.dto.astrbot.AstrBotSessionRuleDto;
import com.example.docmind.dto.astrbot.AstrBotSimulateRequest;
import com.example.docmind.dto.astrbot.AstrBotSimulateResponse;
import com.example.docmind.service.astrbot.AstrBotService;
import com.example.docmind.service.evolution.CustomerSelfEvolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AstrBotServiceTest {

    private CustomerChatService customerChatService;
    private CustomerSelfEvolutionService evolutionService;
    private AstrBotProperties properties;
    private AstrBotService astrBotService;

    @BeforeEach
    void setUp() {
        customerChatService = mock(CustomerChatService.class);
        evolutionService = mock(CustomerSelfEvolutionService.class);
        properties = new AstrBotProperties();

        when(evolutionService.getStatus()).thenReturn(new CustomerEvolutionStatusResponse(
                true, "evoagentx-textgrad-mipro", 5, 42L, 10L, 0.94,
                Map.of(), Map.of(), List.of()
        ));

        when(customerChatService.chat(any(CustomerChatRequest.class))).thenAnswer(inv -> {
            CustomerChatRequest req = inv.getArgument(0);
            return new CustomerChatResponse(
                    "Dạ Christian Dior xin kính chào Quý khách! Tư vấn cho câu hỏi: " + req.question(),
                    "Chuyên viên Dior",
                    req.scenario() != null ? req.scenario() : "SALES",
                    "SALES_ADVISOR_PROMPT",
                    "codex/gpt-6-astra",
                    List.of("Giá bán bao nhiêu?", "Chính sách đổi trả?"),
                    List.of(new Citation("doc-dior-01", "dior_sop_handbook.pdf", List.of("Skincare"), 1, List.of(), List.of("b1"), "Dior Prestige")),
                    "int-dior-01",
                    5,
                    2
            );
        });

        astrBotService = new AstrBotService(properties, customerChatService, evolutionService);
    }

    @Test
    @DisplayName("Danh mục AstrBot cung cấp đủ 19 loại Platform Adapters kèm schema và default_config")
    void catalogProvidesAll19PlatformTypes() {
        List<AstrBotPlatformTypeDto> types = astrBotService.listPlatformTypes();
        assertEquals(19, types.size());
        assertTrue(types.stream().anyMatch(t -> "telegram".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "discord".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "slack".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "lark".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "dingtalk".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "line".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "wecom".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "wecom_ai_bot".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "weixin_official_account".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "weixin_oc".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "qq_official".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "qq_official_webhook".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "aiocqhttp".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "satori".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "kook".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "mattermost".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "misskey".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "facebook_messenger".equals(t.type())));
        assertTrue(types.stream().anyMatch(t -> "webchat".equals(t.type())));
    }

    @Test
    @DisplayName("Tạo Bot mới tự động làm sạch ký tự ':' và '!' trong ID và cấp phát webhook_uuid")
    void createUpdateToggleAndDeleteBotLifecycle() {
        AstrBotInstanceDto created = astrBotService.createBot(Map.of(
                "id", "kook:gaming!club",
                "type", "kook",
                "enable", true,
                "default_persona_id", "dior_luxury_advisor",
                "default_scenario", "SALES",
                "config", Map.of("kook_bot_token", "kook-secret-token-123")
        ));

        assertEquals("kook_gaming_club", created.id());
        assertEquals("kook", created.type());
        assertEquals("running", created.status());

        // Test connection should be ok since kook_bot_token is provided
        Map<String, Object> testResult = astrBotService.testBotConnection("kook_gaming_club");
        assertEquals("ok", testResult.get("status"));

        // Disable bot
        AstrBotInstanceDto disabled = astrBotService.setBotEnabled("kook_gaming_club", false);
        assertFalse(disabled.enable());
        assertEquals("stopped", disabled.status());

        // Duplicate creation should throw
        assertThrows(IllegalArgumentException.class, () -> astrBotService.createBot(Map.of(
                "id", "kook_gaming_club",
                "type", "kook"
        )));

        // Delete bot
        astrBotService.deleteBot("kook_gaming_club");
        assertThrows(IllegalArgumentException.class, () -> astrBotService.getBot("kook_gaming_club"));
    }

    @Test
    @DisplayName("Đăng ký nhanh bằng mã QR (start -> poll) cho Lark / WeChat iLink tự động cấp thông tin kết nối")
    void qrRegistrationStartAndPollFlow() {
        Map<String, Object> startRes = astrBotService.handlePlatformRegistration("lark", Map.of(
                "action", "start",
                "bot_id", "lark_feishu_workspace"
        ));
        assertEquals("pending", startRes.get("status"));
        assertNotNull(startRes.get("qrcode_img_content"));

        Map<String, Object> pollRes = astrBotService.handlePlatformRegistration("lark", Map.of(
                "action", "poll",
                "bot_id", "lark_feishu_workspace"
        ));
        assertEquals("completed", pollRes.get("status"));
        assertNotNull(pollRes.get("credentials"));
    }

    @Test
    @DisplayName("Giả lập tin nhắn đa nền tảng tạo phiên UMO chuẩn, truyền lịch sử sang CustomerChatService và ghi Trace")
    void simulateMessageRoutesThroughCustomerChatAndRecordsTrace() {
        AstrBotSimulateRequest req = new AstrBotSimulateRequest(
                "telegram_dior_concierge",
                "telegram",
                "PrivateMessage",
                "chat_9988",
                "user_9988",
                "Nguyễn Lan Khuê",
                "Tư vấn cho mình nước hoa Dior nữ giữ mùi lâu",
                "SALES",
                "dior_luxury_advisor"
        );

        AstrBotSimulateResponse resp = astrBotService.simulateOrProcessMessage(req);
        assertEquals("telegram_dior_concierge:PrivateMessage:chat_9988", resp.umo());
        assertFalse(resp.isCommand());
        assertTrue(resp.replySent().contains("Christian Dior"));
        assertEquals(1, resp.citations().size());
        assertEquals(5, resp.evoGeneration());

        // Turn 2 in the same UMO session must include 2 history messages (user + assistant from Turn 1)
        ArgumentCaptor<CustomerChatRequest> captor = ArgumentCaptor.forClass(CustomerChatRequest.class);
        astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "telegram_dior_concierge",
                "telegram",
                "PrivateMessage",
                "chat_9988",
                "user_9988",
                "Nguyễn Lan Khuê",
                "Bản 50ml giá bao nhiêu?",
                "SALES",
                "dior_luxury_advisor"
        ));
        verify(customerChatService, org.mockito.Mockito.atLeast(2)).chat(captor.capture());
        CustomerChatRequest secondCall = captor.getValue();
        assertNotNull(secondCall.history());
        assertEquals(2, secondCall.history().size());
    }

    @Test
    @DisplayName("Hỗ trợ đầy đủ bộ lệnh quản trị nội bộ Star Slash Commands (/help, /status, /persona, /scenario, /reset, /whoami)")
    void starSlashCommandsExecution() {
        AstrBotSimulateResponse helpResp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "discord_community_hub", "discord", "GroupMessage", "chan_1", "u1", "DevUser", "/help", null, null
        ));
        assertTrue(helpResp.isCommand());
        assertTrue(helpResp.replySent().contains("/status"));

        AstrBotSimulateResponse personaResp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "discord_community_hub", "discord", "GroupMessage", "chan_1", "u1", "DevUser", "/persona vip_aftersales_concierge", null, null
        ));
        assertTrue(personaResp.isCommand());
        assertTrue(personaResp.replySent().contains("vip_aftersales_concierge"));

        AstrBotSimulateResponse resetResp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "discord_community_hub", "discord", "GroupMessage", "chan_1", "u1", "DevUser", "/reset", null, null
        ));
        assertTrue(resetResp.isCommand());
        assertTrue(resetResp.replySent().contains("làm mới"));
    }

    @Test
    @DisplayName("Unified Webhook xử lý chuẩn xác Telegram, Slack url_verification và LINE events")
    void unifiedWebhookHandlesPlatformPayloads() {
        AstrBotInstanceDto tgBot = astrBotService.getBot("telegram_dior_concierge");
        String webhookUuid = tgBot.webhookUuid();

        // 1. GET challenge
        String challenge = astrBotService.verifyUnifiedWebhookGet(webhookUuid, Map.of("hub.challenge", "meta_chal_77"));
        assertEquals("meta_chal_77", challenge);

        // 2. POST Slack url_verification
        Map<String, Object> slackVerify = astrBotService.handleUnifiedWebhookPost(
                webhookUuid, "{\"type\":\"url_verification\",\"challenge\":\"slack_chal_99\"}");
        assertEquals("slack_chal_99", slackVerify.get("challenge"));

        // 3. POST Telegram Update
        String tgJson = """
                {
                  "update_id": 1001,
                  "message": {
                    "message_id": 55,
                    "from": { "id": 778899, "first_name": "Minh" },
                    "chat": { "id": 778899, "type": "private" },
                    "text": "Tư vấn son Rouge Dior 999"
                  }
                }
                """;
        Map<String, Object> tgRes = astrBotService.handleUnifiedWebhookPost(webhookUuid, tgJson);
        assertEquals("ok", tgRes.get("status"));
        assertEquals("telegram_dior_concierge:PrivateMessage:778899", tgRes.get("umo"));
    }

    @Test
    @DisplayName("Quản lý Personas, Providers, Cron Jobs, Extensions và Overview Snapshot hoạt động trọn vẹn")
    void managementEntitiesCrudAndOverview() {
        // Persona
        AstrBotPersonaDto p = astrBotService.savePersona(Map.of(
                "id", "custom_vip_persona",
                "name", "Trợ lý VIP Custom",
                "system_prompt", "Luôn trả lời ngắn gọn, sang trọng."
        ));
        assertEquals("custom_vip_persona", p.id());

        // Provider
        AstrBotProviderDto pr = astrBotService.saveProvider(Map.of(
                "id", "deepseek-v3-source",
                "provider", "DeepSeek",
                "model", "deepseek-chat",
                "api_key", "sk-1234567890abcdef"
        ));
        assertEquals("deepseek-v3-source", pr.id());
        assertTrue(pr.maskedKey().contains("••••"));

        // Cron Job runNow
        AstrBotCronJobDto job = astrBotService.runCronJobNow("cron-dior-vip-followup");
        assertEquals("succeeded", job.lastStatus());
        assertTrue(job.runCount() >= 5);

        // Extension toggle
        AstrBotExtensionDto ext = astrBotService.toggleExtension("star-docmind-rag-bridge", false);
        assertFalse(ext.enabled());

        // Session rule
        AstrBotSessionRuleDto rule = astrBotService.upsertSessionRule(Map.of(
                "umo", "slack_enterprise_desk:GroupMessage:C999",
                "custom_name", "Kênh CSKH Slack",
                "llm_enabled", false
        ));
        assertFalse(rule.llmEnabled());

        AstrBotOverviewResponse overview = astrBotService.getOverview();
        assertTrue(overview.enabled());
        assertEquals(19, overview.botTypes().size());
        assertFalse(overview.bots().isEmpty());
        assertFalse(overview.traces().isEmpty());
        assertFalse(overview.logs().isEmpty());
    }
}

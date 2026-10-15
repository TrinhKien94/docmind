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

    @Test
    @DisplayName("Contract DTO cho Frontend: fields, doc_url, category 6 nhóm, umo_routes, active_sessions và 6 stages")
    void frontendDtoContractAliasesAndCategoriesArePopulated() {
        List<String> validCategories = List.of(
                "Global Messaging",
                "Enterprise Workspace",
                "WeChat Ecosystem",
                "QQ & OneBot Ecosystem",
                "Open Federation & Protocol",
                "Built-in Channel"
        );
        for (AstrBotPlatformTypeDto pt : astrBotService.listPlatformTypes()) {
            assertTrue(validCategories.contains(pt.category()), "Invalid category for " + pt.type() + ": " + pt.category());
            assertNotNull(pt.docUrl());
            assertFalse(pt.fields().isEmpty(), "fields() must not be empty for " + pt.type());
            Map<String, Object> firstField = pt.fields().get(0);
            assertNotNull(firstField.get("key"));
            assertNotNull(firstField.get("label"));
            assertNotNull(firstField.get("type"));
        }

        AstrBotInstanceDto tgBot = astrBotService.getBot("telegram_dior_concierge");
        assertFalse(tgBot.umoRoutes().isEmpty());
        assertTrue(tgBot.activeSessions() >= 1);

        assertFalse(astrBotService.getTraces().isEmpty());
        var firstTrace = astrBotService.getTraces().get(0);
        assertNotNull(firstTrace.question());
        assertNotNull(firstTrace.replyExcerpt());
        assertNotNull(firstTrace.scenario());
        assertNotNull(firstTrace.personaId());
        assertEquals(6, firstTrace.stages().size());
    }

    @Test
    @DisplayName("updateBot ưu tiên enable và unified_webhook ở top-level thay vì giá trị cũ trong editForm.config")
    void updateBotHonorsTopLevelEnableAndUnifiedWebhookOverStaleConfig() {
        AstrBotInstanceDto updated = astrBotService.updateBot("telegram_dior_concierge", Map.of(
                "enable", false,
                "unified_webhook", false,
                "config", Map.of(
                        "bot_token", "889900:AAH-updated-token",
                        "enable", true,
                        "unified_webhook", true
                )
        ));
        assertFalse(updated.enable());
        assertEquals("stopped", updated.status());
        assertFalse(updated.unifiedWebhook());
        assertEquals(false, updated.config().get("enable"));
        assertEquals(false, updated.config().get("unified_webhook"));
    }

    @Test
    @DisplayName("Định tuyến UMO hỗ trợ ký tự đại diện (*) và không bị kịch bản AUTO làm lu mờ")
    void wildcardUmoRoutingAndAutoScenarioResolution() {
        astrBotService.upsertRoute(Map.of(
                "umo", "telegram_dior_concierge:PrivateMessage:*",
                "config_id", "dior_luxury_default",
                "persona_id", "vip_aftersales_concierge",
                "scenario", "WARRANTY_RETURN"
        ));

        AstrBotSimulateResponse resp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "telegram_dior_concierge",
                "telegram",
                "PrivateMessage",
                "wildcard_vip_user_42",
                "wildcard_vip_user_42",
                "Khách VIP Wildcard",
                "Hộp nước hoa của mình bị móp góc khi vận chuyển",
                "AUTO",
                null
        ));

        assertEquals("WARRANTY_RETURN", resp.resolvedScenario());
        assertEquals("WARRANTY_RETURN", resp.scenario());
        assertEquals("vip_aftersales_concierge", resp.resolvedPersonaId());
        assertEquals("vip_aftersales_concierge", resp.personaId());
        assertEquals("telegram_dior_concierge", resp.platformId());
        assertEquals(6, resp.stages().size());
    }

    @Test
    @DisplayName("Các lệnh Star mở rộng (/provider, /kb, /guidelines, /tts) và gắn nhãn TTS khi bật tts_enabled")
    void extendedStarSlashCommandsAndTtsSynthesisTag() {
        AstrBotSimulateResponse provResp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "telegram_dior_concierge", "telegram", "PrivateMessage", "tts_user_1", "tts_user_1", "VIP", "/provider", "AUTO", null
        ));
        assertTrue(provResp.isCommand());
        assertTrue(provResp.replySent().contains("LLM Provider"));

        AstrBotSimulateResponse kbResp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "telegram_dior_concierge", "telegram", "PrivateMessage", "tts_user_1", "tts_user_1", "VIP", "/kb Sauvage", "AUTO", null
        ));
        assertTrue(kbResp.isCommand());
        assertTrue(kbResp.replySent().contains("Sauvage"));

        AstrBotSimulateResponse guideResp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "telegram_dior_concierge", "telegram", "PrivateMessage", "tts_user_1", "tts_user_1", "VIP", "/guidelines", "AUTO", null
        ));
        assertTrue(guideResp.isCommand());
        assertTrue(guideResp.replySent().contains("EvoAgentX"));

        // Toggle TTS on via /tts
        AstrBotSimulateResponse ttsResp = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "telegram_dior_concierge", "telegram", "PrivateMessage", "tts_user_1", "tts_user_1", "VIP", "/tts", "AUTO", null
        ));
        assertTrue(ttsResp.isCommand());
        assertTrue(ttsResp.replySent().contains("BẬT"));

        // Normal message should now carry TTS synthesis tag
        AstrBotSimulateResponse chatWithTts = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                "telegram_dior_concierge", "telegram", "PrivateMessage", "tts_user_1", "tts_user_1", "VIP", "Tư vấn nước hoa J'adore", "AUTO", null
        ));
        assertFalse(chatWithTts.isCommand());
        assertTrue(chatWithTts.replySent().contains("TTS Audio Ready"));
    }

    @Test
    @DisplayName("Xoá Persona và Provider tự động dọn dẹp tham chiếu mồ côi trên Bot, Session và Route")
    void deletePersonaAndProviderCleansUpDanglingReferences() {
        astrBotService.savePersona(Map.of("id", "temp_persona", "name", "Temp Persona", "system_prompt", "Temp"));
        astrBotService.saveProvider(Map.of("id", "temp_provider", "provider", "Temp", "model", "gpt-temp"));

        astrBotService.updateBot("telegram_dior_concierge", Map.of(
                "default_persona_id", "temp_persona",
                "fallback_profile_id", "temp_provider"
        ));
        astrBotService.upsertRoute(Map.of(
                "umo", "telegram_dior_concierge:PrivateMessage:temp_sess",
                "persona_id", "temp_persona",
                "config_id", "temp_provider",
                "scenario", "SALES"
        ));
        astrBotService.upsertSessionRule(Map.of(
                "umo", "telegram_dior_concierge:PrivateMessage:temp_sess",
                "persona_id", "temp_persona",
                "config_profile_id", "temp_provider"
        ));

        astrBotService.deletePersona("temp_persona");
        astrBotService.deleteProvider("temp_provider");

        AstrBotInstanceDto botAfter = astrBotService.getBot("telegram_dior_concierge");
        assertFalse("temp_persona".equals(botAfter.defaultPersonaId()));
        assertFalse("temp_provider".equals(botAfter.fallbackProfileId()));
    }

    @Test
    @DisplayName("Unified Webhook chặn Bot đang tắt, xử lý mảng JSON và từ chối payload quá lớn")
    void webhookEdgeCasesDisabledBotArrayPayloadAndOversizedGuard() {
        AstrBotInstanceDto tgBot = astrBotService.getBot("telegram_dior_concierge");
        String uuid = tgBot.webhookUuid();

        // JSON array payload works
        Map<String, Object> arrRes = astrBotService.handleUnifiedWebhookPost(uuid, "[{\"message\":\"Xin chào từ mảng JSON\"}]");
        assertEquals("ok", arrRes.get("status"));

        // Malformed JSON increments error count and returns ignored
        Map<String, Object> badJsonRes = astrBotService.handleUnifiedWebhookPost(uuid, "{invalid_json");
        assertEquals("ignored", badJsonRes.get("status"));
        assertEquals("invalid_json", badJsonRes.get("reason"));
        assertTrue(astrBotService.getBot("telegram_dior_concierge").errorCount() >= 1);

        astrBotService.clearBotErrors("telegram_dior_concierge");
        assertEquals(0, astrBotService.getBot("telegram_dior_concierge").errorCount());

        // Disabled bot returns ignored
        astrBotService.setBotEnabled("telegram_dior_concierge", false);
        Map<String, Object> disabledRes = astrBotService.handleUnifiedWebhookPost(uuid, "{\"message\":\"Hello\"}");
        assertEquals("ignored", disabledRes.get("status"));
        assertEquals("bot_disabled", disabledRes.get("reason"));

        // Oversized payload > 256KB throws IllegalArgumentException
        astrBotService.setBotEnabled("telegram_dior_concierge", true);
        String hugePayload = "{\"message\":\"" + "A".repeat(270_000) + "\"}";
        assertThrows(IllegalArgumentException.class, () -> astrBotService.handleUnifiedWebhookPost(uuid, hugePayload));
    }

    @Test
    @DisplayName("Lưu trữ và khôi phục trạng thái AstrBot từ đĩa (persistToDisk = true)")
    void diskPersistenceSaveAndLoadRoundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tempDir) {
        AstrBotProperties diskProps = new AstrBotProperties();
        diskProps.setPersistToDisk(true);
        diskProps.setStateFilePath(tempDir.resolve("astrbot-state.json").toString());

        AstrBotService service1 = new AstrBotService(diskProps, customerChatService, evolutionService);
        service1.createBot(Map.of(
                "id", "mattermost_sec_ops",
                "type", "mattermost",
                "enable", true,
                "default_persona_id", "docmind_rag_architect",
                "default_scenario", "SERVICE_SUPPORT"
        ));
        service1.savePersona(Map.of(
                "id", "persisted_persona",
                "name", "Persisted Persona",
                "system_prompt", "Persisted prompt"
        ));

        // Reload in a second service instance
        AstrBotService service2 = new AstrBotService(diskProps, customerChatService, evolutionService);
        AstrBotInstanceDto restoredBot = service2.getBot("mattermost_sec_ops");
        assertNotNull(restoredBot);
        assertEquals("mattermost", restoredBot.type());
        assertTrue(service2.listPersonas().stream().anyMatch(p -> "persisted_persona".equals(p.id())));
    }
}

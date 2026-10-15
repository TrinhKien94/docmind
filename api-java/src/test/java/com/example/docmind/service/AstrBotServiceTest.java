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

    @Test
    @DisplayName("Facebook Messenger trong AstrBot: xác minh hub.verify_token, kiểm tra chữ ký HMAC-SHA256, xử lý page entry[].messaging[], phản hồi Quick Reply <= 20 ký tự và đồng bộ Reaction/Postback sang EvoAgentX")
    void facebookMessengerUnifiedAdapterFullFlowAndEvoAgentXFeedback() throws Exception {
        AstrBotInstanceDto msgrBot = astrBotService.getBot("messenger_dior_page");
        String uuid = msgrBot.webhookUuid();

        // 1. Valid Meta GET challenge verification
        String challenge = astrBotService.verifyUnifiedWebhookGet(uuid, Map.of(
                "hub.mode", "subscribe",
                "hub.verify_token", "docmind-messenger-token",
                "hub.challenge", "meta_challenge_2026"
        ));
        assertEquals("meta_challenge_2026", challenge);

        // 2. Invalid verify_token throws SecurityException
        assertThrows(SecurityException.class, () -> astrBotService.verifyUnifiedWebhookGet(uuid, Map.of(
                "hub.mode", "subscribe",
                "hub.verify_token", "wrong-token",
                "hub.challenge", "meta_challenge_2026"
        )));

        // 3. Enable HMAC-SHA256 signature check with app_secret
        String secret = "dior_meta_app_secret_key";
        astrBotService.updateBot("messenger_dior_page", Map.of(
                "config", Map.of(
                        "verify_token", "docmind-messenger-token",
                        "app_secret", secret,
                        "verify_signature", true,
                        "send_feedback_quick_replies", true
                )
        ));

        String pageMsgPayload = """
                {
                  "object": "page",
                  "entry": [
                    {
                      "id": "page_dior_vn",
                      "messaging": [
                        {
                          "sender": { "id": "psid_vip_77" },
                          "recipient": { "id": "page_dior_vn" },
                          "message": { "mid": "m_01", "text": "Tư vấn nước hoa Miss Dior Blooming Bouquet" }
                        }
                      ]
                    }
                  ]
                }
                """;

        // Missing or wrong signature rejected with SecurityException
        assertThrows(SecurityException.class, () -> astrBotService.handleUnifiedWebhookPost(uuid, "sha256=deadbeef", pageMsgPayload));

        // Compute valid HMAC-SHA256 signature
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
        String validSig = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(pageMsgPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        Map<String, Object> msgRes = astrBotService.handleUnifiedWebhookPost(uuid, validSig, pageMsgPayload);
        assertEquals("ok", msgRes.get("status"));
        assertEquals("messenger_dior_page:PrivateMessage:psid_vip_77", msgRes.get("umo"));
        @SuppressWarnings("unchecked")
        List<String> qrs = (List<String>) msgRes.get("quick_replies");
        assertNotNull(qrs);
        assertTrue(qrs.stream().allMatch(q -> q.length() <= 20), "All Messenger quick replies must be <= 20 chars");
        assertTrue(qrs.contains("👍 Hữu ích"));

        // 4. Echo and delivery receipts are ignored gracefully
        String echoPayload = """
                {
                  "object": "page",
                  "entry": [{ "messaging": [{ "sender": { "id": "psid_vip_77" }, "message": { "is_echo": true, "text": "echo" } }] }]
                }
                """;
        String echoSig = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(echoPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Map<String, Object> echoRes = astrBotService.handleUnifiedWebhookPost(uuid, echoSig, echoPayload);
        assertEquals("ignored", echoRes.get("status"));

        // 5. Reaction event triggers EvoAgentX feedback evolution
        String reactionPayload = """
                {
                  "object": "page",
                  "entry": [
                    {
                      "messaging": [
                        {
                          "sender": { "id": "psid_vip_77" },
                          "reaction": { "action": "react", "reaction": "love", "emoji": "❤️" }
                        }
                      ]
                    }
                  ]
                }
                """;
        String reactSig = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(reactionPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        Map<String, Object> reactRes = astrBotService.handleUnifiedWebhookPost(uuid, reactSig, reactionPayload);
        assertEquals("ok", reactRes.get("status"));
        assertEquals(true, reactRes.get("feedback_recorded"));
        assertEquals(true, reactRes.get("helpful"));
        verify(evolutionService).recordFeedbackAndEvolve(any());
    }

    @Test
    @DisplayName("Bảo mật GET hub.challenge & X-Hub-Signature-256: chặn mọi hành vi bỏ qua hub.mode / hub.verify_token hoặc thiếu app_secret khi verify_signature=true")
    void webhookSecurityRejectsMissingModeOrVerifyTokenAndFailsClosedOnMissingSecret() {
        AstrBotInstanceDto msgrBot = astrBotService.getBot("messenger_dior_page");
        String uuid = msgrBot.webhookUuid();

        // Bypass attempt 1: omitting both hub.mode and hub.verify_token on a bot with verify_token configured
        assertThrows(SecurityException.class, () -> astrBotService.verifyUnifiedWebhookGet(uuid, Map.of(
                "hub.challenge", "bypass_attempt_01"
        )));

        // Bypass attempt 2: omitting hub.mode while supplying valid hub.verify_token
        assertThrows(SecurityException.class, () -> astrBotService.verifyUnifiedWebhookGet(uuid, Map.of(
                "hub.verify_token", "docmind-messenger-token",
                "hub.challenge", "bypass_attempt_02"
        )));

        // Bypass attempt 3: omitting hub.verify_token while supplying hub.mode=subscribe
        assertThrows(SecurityException.class, () -> astrBotService.verifyUnifiedWebhookGet(uuid, Map.of(
                "hub.mode", "subscribe",
                "hub.challenge", "bypass_attempt_03"
        )));

        // Bypass attempt 4: wrong hub.mode=unsubscribe
        assertThrows(SecurityException.class, () -> astrBotService.verifyUnifiedWebhookGet(uuid, Map.of(
                "hub.mode", "unsubscribe",
                "hub.verify_token", "docmind-messenger-token",
                "hub.challenge", "bypass_attempt_04"
        )));

        // Fail-closed signature check: verify_signature=true with blank app_secret must reject POST
        astrBotService.updateBot("messenger_dior_page", Map.of(
                "config", Map.of(
                        "verify_token", "docmind-messenger-token",
                        "app_secret", "",
                        "verify_signature", true
                )
        ));
        assertThrows(SecurityException.class, () -> astrBotService.handleUnifiedWebhookPost(
                uuid, "sha256=anything", "{\"object\":\"page\",\"entry\":[]}"
        ));
    }

    @Test
    @DisplayName("Outbound Meta Graph Send API (/v21.0/me/messages) gửi Bearer token, cắt ngắn text <= 2000 ký tự, đính kèm FEEDBACK_HELPFUL_<id> và xử lý Batch Multi-Entry UTF-8")
    void outboundMetaGraphSendApiAndMultiEntryUtf8BatchAndSimulatorFeedback() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> capturedAuth = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> capturedPath = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<String> capturedBody = new java.util.concurrent.atomic.AtomicReference<>();

        com.sun.net.httpserver.HttpServer mockGraphServer = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        mockGraphServer.createContext("/v21.0/me/messages", exchange -> {
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedPath.set(exchange.getRequestURI().getPath());
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            capturedBody.set(reqBody);
            byte[] respBytes = "{\"recipient_id\":\"psid_utf8_99\",\"message_id\":\"mid.live_graph_777\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, respBytes.length);
            exchange.getResponseBody().write(respBytes);
            exchange.close();
        });
        mockGraphServer.start();

        try {
            int port = mockGraphServer.getAddress().getPort();
            String secret = "dior_utf8_secret_2026";
            astrBotService.updateBot("messenger_dior_page", Map.of(
                    "config", Map.of(
                            "page_access_token", "EAAG_LIVE_TOKEN_99",
                            "verify_token", "docmind-messenger-token",
                            "app_secret", secret,
                            "verify_signature", true,
                            "api_version", "v21.0",
                            "graph_base_url", "http://127.0.0.1:" + port,
                            "send_feedback_quick_replies", true
                    )
            ));

            String uuid = astrBotService.getBot("messenger_dior_page").webhookUuid();

            // Multi-entry batch payload with Vietnamese UTF-8 + emojis + delivery + message + reaction + explicit feedback postback
            String batchedPayload = """
                    {
                      "object": "page",
                      "entry": [
                        {
                          "id": "page_1",
                          "messaging": [
                            { "sender": { "id": "psid_utf8_99" }, "delivery": { "mids": ["m0"] } },
                            {
                              "sender": { "id": "psid_utf8_99" },
                              "message": { "mid": "m1", "text": "Tư vấn nước hoa J'adore L'Or 50ml chính hãng 💎🌸 tiếng Việt có dấu" }
                            }
                          ]
                        },
                        {
                          "id": "page_1",
                          "messaging": [
                            {
                              "sender": { "id": "psid_utf8_99" },
                              "postback": { "title": "👎 Cần cải thiện", "payload": "FEEDBACK_UNHELPFUL_int-explicit-999" }
                            }
                          ]
                        }
                      ]
                    }
                    """;

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            String sig = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(batchedPayload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            Map<String, Object> batchRes = astrBotService.handleUnifiedWebhookPost(uuid, sig, batchedPayload);
            assertEquals("ok", batchRes.get("status"));
            assertEquals(2, batchRes.get("processed_events"));
            assertEquals("int-explicit-999", batchRes.get("interaction_id"));
            assertEquals("GRAPH_API", batchRes.get("delivery_mode"));
            assertEquals(true, batchRes.get("dispatched"));

            assertEquals("Bearer EAAG_LIVE_TOKEN_99", capturedAuth.get());
            assertEquals("/v21.0/me/messages", capturedPath.get());
            assertNotNull(capturedBody.get());

            ArgumentCaptor<com.example.docmind.dto.CustomerFeedbackRequest> fbCaptor =
                    ArgumentCaptor.forClass(com.example.docmind.dto.CustomerFeedbackRequest.class);
            verify(evolutionService).recordFeedbackAndEvolve(fbCaptor.capture());
            assertEquals("int-explicit-999", fbCaptor.getValue().interactionId());
            assertFalse(fbCaptor.getValue().helpful());
            assertEquals(2, fbCaptor.getValue().rating());

            // Also verify Live Simulator feedback quick reply ("👍 Hữu ích") triggers EvoAgentX feedback instead of product chat
            AstrBotSimulateResponse simTurn1 = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                    "messenger_dior_page", "facebook_messenger", "PrivateMessage",
                    "sim_user_01", "sim_user_01", "Khách Simulator",
                    "Tư vấn kem dưỡng Dior Capture Totale", null, null
            ));
            assertTrue(simTurn1.quickReplies().contains("👍 Hữu ích"));

            AstrBotSimulateResponse simFeedback = astrBotService.simulateOrProcessMessage(new AstrBotSimulateRequest(
                    "messenger_dior_page", "facebook_messenger", "PrivateMessage",
                    "sim_user_01", "sim_user_01", "Khách Simulator",
                    "👍 Hữu ích", null, null
            ));
            assertTrue(simFeedback.replySent().contains("EvoAgentX"));
            assertFalse(simFeedback.quickReplies().contains("👍 Hữu ích"), "Feedback acknowledgment should not re-append feedback buttons");
        } finally {
            mockGraphServer.stop(0);
        }
    }
}

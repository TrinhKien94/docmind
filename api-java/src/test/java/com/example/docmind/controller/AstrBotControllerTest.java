package com.example.docmind.controller;

import com.example.docmind.dto.astrbot.AstrBotOverviewResponse;
import com.example.docmind.dto.astrbot.AstrBotSimulateRequest;
import com.example.docmind.dto.astrbot.AstrBotSimulateResponse;
import com.example.docmind.service.astrbot.AstrBotPlatformCatalog;
import com.example.docmind.service.astrbot.AstrBotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AstrBotController.class)
class AstrBotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AstrBotService astrBotService;

    @Test
    @DisplayName("GET /api/astrbot/overview trả về snapshot toàn bộ bảng điều khiển AstrBot")
    void getOverviewReturnsDashboardSnapshot() throws Exception {
        AstrBotOverviewResponse mockOverview = new AstrBotOverviewResponse(
                true,
                "v4.14.0-docmind",
                "Asia/Ho_Chi_Minh",
                "/api/astrbot/webhooks/platforms",
                Map.of("total_messages", 42, "running_bots", 6),
                AstrBotPlatformCatalog.buildPlatformTypes(),
                List.of(),
                List.of(),
                AstrBotPlatformCatalog.buildDefaultPersonas(),
                AstrBotPlatformCatalog.buildDefaultProviders(),
                AstrBotPlatformCatalog.buildDefaultCronJobs(),
                AstrBotPlatformCatalog.buildDefaultExtensions(),
                List.of(),
                List.of()
        );
        when(astrBotService.getOverview()).thenReturn(mockOverview);

        mockMvc.perform(get("/api/astrbot/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.version").value("v4.14.0-docmind"))
                .andExpect(jsonPath("$.bot_types.length()").value(19))
                .andExpect(jsonPath("$.bot_types[0].category").value("Global Messaging"))
                .andExpect(jsonPath("$.bot_types[0].doc_url").isNotEmpty())
                .andExpect(jsonPath("$.bot_types[0].fields").isArray())
                .andExpect(jsonPath("$.bot_types[0].fields[0].key").isNotEmpty())
                .andExpect(jsonPath("$.bot_types[0].fields[0].label").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/astrbot/simulate thực thi pipeline đa nền tảng và trả về kết quả")
    void simulateEndpointReturnsSimulatedReply() throws Exception {
        AstrBotSimulateResponse resp = new AstrBotSimulateResponse(
                "astr-1234",
                "telegram_dior_concierge:PrivateMessage:u1",
                "telegram_dior_concierge",
                "telegram",
                "PrivateMessage",
                "u1",
                "Khách VIP",
                "Xin chào",
                "Dạ Christian Dior xin kính chào Quý khách!",
                "SALES",
                "dior_luxury_advisor",
                "dior_luxury_default",
                false,
                List.of(),
                List.of("Tư vấn thêm"),
                4,
                0.96,
                25L,
                true,
                "2026-10-15T11:00:00Z"
        );
        when(astrBotService.simulateOrProcessMessage(any(AstrBotSimulateRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/astrbot/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bot_id\":\"telegram_dior_concierge\",\"message\":\"Xin chào\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.umo").value("telegram_dior_concierge:PrivateMessage:u1"))
                .andExpect(jsonPath("$.platform_id").value("telegram_dior_concierge"))
                .andExpect(jsonPath("$.incoming_message").value("Xin chào"))
                .andExpect(jsonPath("$.scenario").value("SALES"))
                .andExpect(jsonPath("$.persona_id").value("dior_luxury_advisor"))
                .andExpect(jsonPath("$.stages.length()").value(6))
                .andExpect(jsonPath("$.reply_sent").value("Dạ Christian Dior xin kính chào Quý khách!"));
    }

    @Test
    @DisplayName("GET & POST /api/astrbot/webhooks/platforms/{uuid} xử lý xác minh và sự kiện Unified Webhook")
    void unifiedWebhookEndpointsWork() throws Exception {
        when(astrBotService.verifyUnifiedWebhookGet(eq("wh_123"), any())).thenReturn("challenge_accepted");
        when(astrBotService.handleUnifiedWebhookPost(eq("wh_123"), any()))
                .thenReturn(Map.of("status", "ok", "event_received", true));

        mockMvc.perform(get("/api/astrbot/webhooks/platforms/wh_123")
                        .param("hub.challenge", "challenge_accepted"))
                .andExpect(status().isOk())
                .andExpect(content().string("challenge_accepted"));

        mockMvc.perform(post("/api/astrbot/webhooks/platforms/wh_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.event_received").value(true));
    }

    @Test
    @DisplayName("DELETE /api/astrbot/sessions xoá phiên UMO thành công")
    void clearSessionsReturnsClearedCount() throws Exception {
        when(astrBotService.clearAllSessions()).thenReturn(5);

        mockMvc.perform(delete("/api/astrbot/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cleared_count").value(5));
    }
}

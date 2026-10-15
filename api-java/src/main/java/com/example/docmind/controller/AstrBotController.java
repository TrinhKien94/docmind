package com.example.docmind.controller;

import com.example.docmind.dto.astrbot.AstrBotCronJobDto;
import com.example.docmind.dto.astrbot.AstrBotExtensionDto;
import com.example.docmind.dto.astrbot.AstrBotInstanceDto;
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
import com.example.docmind.service.astrbot.AstrBotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST Controller cung cấp toàn bộ API kết nối đa nền tảng chat & bảng điều khiển quản trị AstrBot
 * (tương ứng astrbot/dashboard/api/bots.py, platform.py, sessions.py, personas.py, providers.py,
 * cron.py, extensions.py, stats.py, logs.py).
 */
@RestController
@RequestMapping("/api/astrbot")
public class AstrBotController {

    private final AstrBotService astrBotService;

    @Autowired
    public AstrBotController(AstrBotService astrBotService) {
        this.astrBotService = astrBotService;
    }

    // =========================================================================
    // 1. OVERVIEW & DASHBOARD SNAPSHOT
    // =========================================================================

    @GetMapping({"/overview", "/status"})
    public ResponseEntity<AstrBotOverviewResponse> getOverview() {
        return ResponseEntity.ok(astrBotService.getOverview());
    }

    // =========================================================================
    // 2. PLATFORM ADAPTER TYPES & BOT INSTANCES (bots.py / platform.py)
    // =========================================================================

    @GetMapping("/bot-types")
    public ResponseEntity<Map<String, Object>> listBotTypes() {
        List<AstrBotPlatformTypeDto> types = astrBotService.listPlatformTypes();
        return ResponseEntity.ok(Map.of("status", "ok", "bot_types", types));
    }

    @GetMapping("/bots")
    public ResponseEntity<Map<String, Object>> listBots(
            @RequestParam(value = "enabled", required = false) Boolean enabled,
            @RequestParam(value = "type", required = false) String type) {
        List<AstrBotInstanceDto> bots = astrBotService.listBots(enabled, type);
        return ResponseEntity.ok(Map.of("status", "ok", "bots", bots));
    }

    @GetMapping("/bots/stats")
    public ResponseEntity<Map<String, Object>> getBotStats() {
        return ResponseEntity.ok(astrBotService.getStats());
    }

    @GetMapping("/bots/{botId}")
    public ResponseEntity<?> getBot(@PathVariable("botId") String botId) {
        try {
            AstrBotInstanceDto bot = astrBotService.getBot(botId);
            return ResponseEntity.ok(Map.of("status", "ok", "bot", bot));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/bots")
    public ResponseEntity<?> createBot(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            AstrBotInstanceDto created = astrBotService.createBot(payload);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "status", "ok",
                    "message", "Đã thêm cấu hình Platform Adapter thành công!",
                    "bot", created
            ));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/bots/{botId}")
    public ResponseEntity<?> updateBot(
            @PathVariable("botId") String botId,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            AstrBotInstanceDto updated = astrBotService.updateBot(botId, payload);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Đã lưu cấu hình và reload Platform Adapter thành công!",
                    "bot", updated
            ));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @RequestMapping(path = "/bots/{botId}/enabled", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<?> setBotEnabled(
            @PathVariable("botId") String botId,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            boolean enabled = payload == null || !payload.containsKey("enabled")
                    || Boolean.parseBoolean(String.valueOf(payload.get("enabled")));
            AstrBotInstanceDto updated = astrBotService.setBotEnabled(botId, enabled);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Đã cập nhật trạng thái hoạt động của [" + botId + "]",
                    "bot", updated
            ));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/bots/{botId}")
    public ResponseEntity<?> deleteBot(@PathVariable("botId") String botId) {
        try {
            astrBotService.deleteBot(botId);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Đã xoá cấu hình nền tảng [" + botId + "] thành công!"
            ));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/bots/{botId}/test")
    public ResponseEntity<?> testBot(@PathVariable("botId") String botId) {
        try {
            return ResponseEntity.ok(astrBotService.testBotConnection(botId));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/bots/{botId}/clear-errors")
    public ResponseEntity<?> clearBotErrors(@PathVariable("botId") String botId) {
        try {
            AstrBotInstanceDto updated = astrBotService.clearBotErrors(botId);
            return ResponseEntity.ok(Map.of("status", "ok", "bot", updated));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/bot-types/{botType}/registration")
    public ResponseEntity<?> registerBotType(
            @PathVariable("botType") String botType,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            return ResponseEntity.ok(astrBotService.handlePlatformRegistration(botType, payload));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // =========================================================================
    // 3. UNIFIED WEBHOOK CALLBACK & LIVE MULTI-PLATFORM SIMULATOR
    // =========================================================================

    @GetMapping(value = "/webhooks/platforms/{webhookUuid}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> handleWebhookGet(
            @PathVariable("webhookUuid") String webhookUuid,
            @RequestParam Map<String, String> queryParams) {
        try {
            String resp = astrBotService.verifyUnifiedWebhookGet(webhookUuid, queryParams);
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    @PostMapping("/webhooks/platforms/{webhookUuid}")
    public ResponseEntity<?> handleWebhookPost(
            @PathVariable("webhookUuid") String webhookUuid,
            @RequestBody(required = false) String rawBody) {
        try {
            return ResponseEntity.ok(astrBotService.handleUnifiedWebhookPost(webhookUuid, rawBody));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/simulate")
    public ResponseEntity<?> simulateMessage(@RequestBody(required = false) AstrBotSimulateRequest request) {
        try {
            AstrBotSimulateResponse response = astrBotService.simulateOrProcessMessage(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // =========================================================================
    // 4. UMO SESSIONS & CUSTOM RULES (sessions.py / config_service.py)
    // =========================================================================

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "message_type", required = false) String messageType,
            @RequestParam(value = "search", required = false) String search) {
        List<AstrBotSessionRuleDto> sessions = astrBotService.listSessions(platform, messageType, search);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "total", sessions.size(),
                "sessions", sessions
        ));
    }

    @PostMapping("/sessions/rules")
    public ResponseEntity<?> upsertSessionRule(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            AstrBotSessionRuleDto rule = astrBotService.upsertSessionRule(payload);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Đã cập nhật quy tắc phiên UMO thành công!",
                    "session", rule
            ));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Map<String, Object>> deleteOrClearSessions(
            @RequestParam(value = "umo", required = false) String umo) {
        if (umo != null && !umo.isBlank()) {
            astrBotService.deleteSession(umo);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã xoá phiên UMO: " + umo));
        }
        int cleared = astrBotService.clearAllSessions();
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Đã làm sạch " + cleared + " phiên hội thoại đa nền tảng",
                "cleared_count", cleared
        ));
    }

    @GetMapping("/routes")
    public ResponseEntity<Map<String, Object>> listRoutes() {
        return ResponseEntity.ok(Map.of("status", "ok", "routes", astrBotService.listRoutes()));
    }

    @PostMapping("/routes")
    public ResponseEntity<?> upsertRoute(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            RouteBindingDto binding = astrBotService.upsertRoute(payload);
            return ResponseEntity.ok(Map.of("status", "ok", "route", binding));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/routes")
    public ResponseEntity<Map<String, Object>> deleteRoute(@RequestParam("umo") String umo) {
        astrBotService.deleteRoute(umo);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã gỡ route cho UMO: " + umo));
    }

    // =========================================================================
    // 5. PERSONAS & LLM PROVIDER SOURCES (personas.py / providers.py)
    // =========================================================================

    @GetMapping("/personas")
    public ResponseEntity<Map<String, Object>> listPersonas() {
        List<AstrBotPersonaDto> personas = astrBotService.listPersonas();
        return ResponseEntity.ok(Map.of("status", "ok", "personas", personas));
    }

    @RequestMapping(path = {"/personas", "/personas/{personaId}"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> savePersona(
            @PathVariable(value = "personaId", required = false) String personaId,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> merged = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
            if (personaId != null && !personaId.isBlank()) {
                merged.put("id", personaId);
            }
            AstrBotPersonaDto saved = astrBotService.savePersona(merged);
            return ResponseEntity.ok(Map.of("status", "ok", "persona", saved));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/personas/{personaId}")
    public ResponseEntity<?> deletePersona(@PathVariable("personaId") String personaId) {
        try {
            astrBotService.deletePersona(personaId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã xoá Persona: " + personaId));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/providers")
    public ResponseEntity<Map<String, Object>> listProviders() {
        List<AstrBotProviderDto> providers = astrBotService.listProviders();
        return ResponseEntity.ok(Map.of("status", "ok", "providers", providers));
    }

    @RequestMapping(path = {"/providers", "/providers/{providerId}"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> saveProvider(
            @PathVariable(value = "providerId", required = false) String providerId,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> merged = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
            if (providerId != null && !providerId.isBlank()) {
                merged.put("id", providerId);
            }
            AstrBotProviderDto saved = astrBotService.saveProvider(merged);
            return ResponseEntity.ok(Map.of("status", "ok", "provider", saved));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/providers/{providerId}/test")
    public ResponseEntity<?> testProvider(@PathVariable("providerId") String providerId) {
        try {
            return ResponseEntity.ok(astrBotService.testProvider(providerId));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/providers/{providerId}")
    public ResponseEntity<?> deleteProvider(@PathVariable("providerId") String providerId) {
        try {
            astrBotService.deleteProvider(providerId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã xoá Provider: " + providerId));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // =========================================================================
    // 6. PROACTIVE CRON JOBS & EXTENSIONS / MCP SERVERS (cron.py / extensions.py)
    // =========================================================================

    @GetMapping("/cron")
    public ResponseEntity<Map<String, Object>> listCronJobs() {
        List<AstrBotCronJobDto> jobs = astrBotService.listCronJobs();
        return ResponseEntity.ok(Map.of("status", "ok", "jobs", jobs));
    }

    @RequestMapping(path = {"/cron", "/cron/{jobId}"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> saveCronJob(
            @PathVariable(value = "jobId", required = false) String jobId,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> merged = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
            if (jobId != null && !jobId.isBlank()) {
                merged.put("job_id", jobId);
            }
            AstrBotCronJobDto saved = astrBotService.saveCronJob(merged);
            return ResponseEntity.ok(Map.of("status", "ok", "job", saved));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/cron/{jobId}/run")
    public ResponseEntity<?> runCronJobNow(@PathVariable("jobId") String jobId) {
        try {
            AstrBotCronJobDto updated = astrBotService.runCronJobNow(jobId);
            return ResponseEntity.ok(Map.of(
                    "status", "ok",
                    "message", "Đã thực thi chủ động tác vụ [" + updated.name() + "] thành công!",
                    "job", updated
            ));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/cron/{jobId}")
    public ResponseEntity<?> deleteCronJob(@PathVariable("jobId") String jobId) {
        try {
            astrBotService.deleteCronJob(jobId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã xoá Cron Job: " + jobId));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/extensions")
    public ResponseEntity<Map<String, Object>> listExtensions() {
        List<AstrBotExtensionDto> extensions = astrBotService.listExtensions();
        return ResponseEntity.ok(Map.of("status", "ok", "extensions", extensions));
    }

    @PostMapping("/extensions")
    public ResponseEntity<?> saveExtension(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            AstrBotExtensionDto saved = astrBotService.saveExtension(payload);
            return ResponseEntity.ok(Map.of("status", "ok", "extension", saved));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @RequestMapping(path = "/extensions/{extensionId}/enabled", method = {RequestMethod.PATCH, RequestMethod.POST})
    public ResponseEntity<?> toggleExtension(
            @PathVariable("extensionId") String extensionId,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            boolean enabled = payload == null || !payload.containsKey("enabled")
                    || Boolean.parseBoolean(String.valueOf(payload.get("enabled")));
            AstrBotExtensionDto updated = astrBotService.toggleExtension(extensionId, enabled);
            return ResponseEntity.ok(Map.of("status", "ok", "extension", updated));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/extensions/{extensionId}")
    public ResponseEntity<?> deleteExtension(@PathVariable("extensionId") String extensionId) {
        try {
            astrBotService.deleteExtension(extensionId);
            return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã gỡ Extension: " + extensionId));
        } catch (IllegalArgumentException e) {
            return errorResponse(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    // =========================================================================
    // 7. STATS, TRACES & CONSOLE LOGS (stats.py / logs.py)
    // =========================================================================

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(astrBotService.getStats());
    }

    @GetMapping("/traces")
    public ResponseEntity<Map<String, Object>> getTraces() {
        List<AstrBotTraceDto> traces = astrBotService.getTraces();
        return ResponseEntity.ok(Map.of("status", "ok", "traces", traces));
    }

    @GetMapping("/logs")
    public ResponseEntity<Map<String, Object>> getLogs(
            @RequestParam(value = "hide_user_chat", defaultValue = "false") boolean hideUserChat) {
        List<AstrBotLogEntryDto> logs = astrBotService.getLogs(hideUserChat);
        return ResponseEntity.ok(Map.of("status", "ok", "logs", logs));
    }

    @DeleteMapping("/logs")
    public ResponseEntity<Map<String, Object>> clearLogs() {
        astrBotService.clearLogs();
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Đã xoá Console Logs"));
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", "error",
                "message", message != null ? message : "Bad Request"
        ));
    }
}

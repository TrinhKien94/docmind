package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Snapshot tổng hợp toàn bộ trạng thái AstrBot Multi-Platform & Management Dashboard.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotOverviewResponse(
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("version") String version,
        @JsonProperty("timezone") String timezone,
        @JsonProperty("webhook_base_path") String webhookBasePath,
        @JsonProperty("stats") Map<String, Object> stats,
        @JsonProperty("bot_types") List<AstrBotPlatformTypeDto> botTypes,
        @JsonProperty("bots") List<AstrBotInstanceDto> bots,
        @JsonProperty("sessions") List<AstrBotSessionRuleDto> sessions,
        @JsonProperty("personas") List<AstrBotPersonaDto> personas,
        @JsonProperty("providers") List<AstrBotProviderDto> providers,
        @JsonProperty("cron_jobs") List<AstrBotCronJobDto> cronJobs,
        @JsonProperty("extensions") List<AstrBotExtensionDto> extensions,
        @JsonProperty("traces") List<AstrBotTraceDto> traces,
        @JsonProperty("logs") List<AstrBotLogEntryDto> logs
) {
}

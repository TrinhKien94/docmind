package com.example.docmind.dto.astrbot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Tác vụ lập lịch chủ động (Cron Job / Proactive Task) theo thiết kế CronJobPage của AstrBot.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AstrBotCronJobDto(
        @JsonProperty("job_id") String jobId,
        @JsonProperty("name") String name,
        @JsonProperty("description") String description,
        @JsonProperty("cron_expression") String cronExpression,
        @JsonProperty("target_umo") String targetUmo,
        @JsonProperty("prompt") String prompt,
        @JsonProperty("scenario") String scenario,
        @JsonProperty("run_once") boolean runOnce,
        @JsonProperty("enabled") boolean enabled,
        @JsonProperty("last_run_at") String lastRunAt,
        @JsonProperty("next_run_at") String nextRunAt,
        @JsonProperty("run_count") int runCount,
        @JsonProperty("last_status") String lastStatus,
        @JsonProperty("last_result_preview") String lastResultPreview
) {
}

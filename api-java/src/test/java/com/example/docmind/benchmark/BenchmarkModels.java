package com.example.docmind.benchmark;

import com.example.docmind.dto.Citation;
import com.example.docmind.service.retrieval.CustomerScenario;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Các mô hình dữ liệu (DTOs & Records) dành riêng cho bộ benchmark 1.000 kịch bản hội thoại DocMind.
 */
public final class BenchmarkModels {

    private BenchmarkModels() {
    }

    /**
     * Mô tả 1 kịch bản hội thoại đơn lẻ trong tập 1.000 kịch bản.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BenchmarkScenario(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("domain") CustomerScenario domain,
            @JsonProperty("sub_category") String subCategory,
            @JsonProperty("platform") String platform,
            @JsonProperty("bot_id") String botId,
            @JsonProperty("channel_type") String channelType,
            @JsonProperty("is_safety_critical") boolean isSafetyCritical,
            @JsonProperty("safety_category") String safetyCategory,
            @JsonProperty("language_variant") String languageVariant,
            @JsonProperty("persona_id") String personaId,
            @JsonProperty("turns") List<BenchmarkTurn> turns
    ) {}

    /**
     * Mô tả 1 lượt (turn) trong kịch bản hội thoại.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BenchmarkTurn(
            @JsonProperty("turn_index") int turnIndex,
            @JsonProperty("user_message") String userMessage,
            @JsonProperty("expected_scenario") CustomerScenario expectedScenario,
            @JsonProperty("expected_secondary_scenarios") List<CustomerScenario> expectedSecondaryScenarios,
            @JsonProperty("requires_anaphora_resolution") boolean requiresAnaphoraResolution,
            @JsonProperty("anaphora_target_entities") List<String> anaphoraTargetEntities,
            @JsonProperty("expected_keywords") List<String> expectedKeywords,
            @JsonProperty("forbidden_keywords") List<String> forbiddenKeywords,
            @JsonProperty("expected_price_vnd") String expectedPriceVnd,
            @JsonProperty("requires_citation_metadata") boolean requiresCitationMetadata,
            @JsonProperty("forbidden_citation_markers") List<String> forbiddenCitationMarkers,
            @JsonProperty("is_slash_command") boolean isSlashCommand,
            @JsonProperty("expected_command") String expectedCommand,
            @JsonProperty("is_adversarial") boolean isAdversarial
    ) {}

    /**
     * Wrapper bao bọc toàn bộ 1.000 kịch bản kèm metadata phân bổ.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BenchmarkSuite(
            @JsonProperty("version") String version,
            @JsonProperty("total_scenarios") int totalScenarios,
            @JsonProperty("generated_at") String generatedAt,
            @JsonProperty("distribution") Map<String, Integer> distribution,
            @JsonProperty("scenarios") List<BenchmarkScenario> scenarios
    ) {}

    /**
     * Kết quả thực thi chi tiết của 1 kịch bản.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScenarioExecutionResult(
            @JsonProperty("scenario_id") String scenarioId,
            @JsonProperty("scenario_name") String scenarioName,
            @JsonProperty("expected_domain") CustomerScenario expectedDomain,
            @JsonProperty("actual_domain") CustomerScenario actualDomain,
            @JsonProperty("detected_secondary_domains") List<CustomerScenario> detectedSecondaryDomains,
            @JsonProperty("platform") String platform,
            @JsonProperty("user_query") String userQuery,
            @JsonProperty("assistant_reply") String assistantReply,
            @JsonProperty("structured_citations") List<Citation> structuredCitations,
            @JsonProperty("latency_ms") long latencyMs,
            @JsonProperty("is_safety_critical") boolean isSafetyCritical,
            @JsonProperty("safety_category") String safetyCategory,
            @JsonProperty("passed") boolean passed,
            @JsonProperty("classification_passed") boolean classificationPassed,
            @JsonProperty("safety_passed") boolean safetyPassed,
            @JsonProperty("citation_hygiene_passed") boolean citationHygienePassed,
            @JsonProperty("rag_relevance_passed") boolean ragRelevancePassed,
            @JsonProperty("anaphora_passed") boolean anaphoraPassed,
            @JsonProperty("adversarial_defense_passed") boolean adversarialDefensePassed,
            @JsonProperty("leaked_citation_markers") List<String> leakedCitationMarkers,
            @JsonProperty("safety_violations") List<String> safetyViolations,
            @JsonProperty("failure_reasons") List<String> failureReasons
    ) {}

    /**
     * Thống kê theo từng miền nghiệp vụ.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DomainMetric(
            @JsonProperty("domain") CustomerScenario domain,
            @JsonProperty("total") int total,
            @JsonProperty("passed") int passed,
            @JsonProperty("failed") int failed,
            @JsonProperty("pass_rate_pct") double passRatePct,
            @JsonProperty("classification_accuracy_pct") double classificationAccuracyPct,
            @JsonProperty("safety_compliance_pct") double safetyCompliancePct,
            @JsonProperty("citation_hygiene_pct") double citationHygienePct,
            @JsonProperty("avg_latency_ms") double avgLatencyMs,
            @JsonProperty("p95_latency_ms") long p95LatencyMs
    ) {}

    /**
     * Thống kê theo từng nền tảng AstrBot.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PlatformMetric(
            @JsonProperty("platform") String platform,
            @JsonProperty("total") int total,
            @JsonProperty("passed") int passed,
            @JsonProperty("failed") int failed,
            @JsonProperty("pass_rate_pct") double passRatePct,
            @JsonProperty("classification_accuracy_pct") double classificationAccuracyPct,
            @JsonProperty("avg_latency_ms") double avgLatencyMs,
            @JsonProperty("p95_latency_ms") long p95LatencyMs
    ) {}

    /**
     * Báo cáo tổng hợp số liệu của toàn bộ đợt chạy benchmark.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BenchmarkSummaryReport(
            @JsonProperty("total_scenarios") int totalScenarios,
            @JsonProperty("passed_scenarios") int passedScenarios,
            @JsonProperty("failed_scenarios") int failedScenarios,
            @JsonProperty("overall_pass_rate_pct") double overallPassRatePct,
            @JsonProperty("classification_accuracy_pct") double classificationAccuracyPct,
            @JsonProperty("critical_safety_compliance_pct") double criticalSafetyCompliancePct,
            @JsonProperty("citation_hygiene_compliance_pct") double citationHygieneCompliancePct,
            @JsonProperty("structured_citations_integrity_pct") double structuredCitationsIntegrityPct,
            @JsonProperty("mean_latency_ms") double meanLatencyMs,
            @JsonProperty("min_latency_ms") long minLatencyMs,
            @JsonProperty("p50_latency_ms") long p50LatencyMs,
            @JsonProperty("p90_latency_ms") long p90LatencyMs,
            @JsonProperty("p95_latency_ms") long p95LatencyMs,
            @JsonProperty("p99_latency_ms") long p99LatencyMs,
            @JsonProperty("max_latency_ms") long maxLatencyMs,
            @JsonProperty("total_duration_ms") long totalDurationMs,
            @JsonProperty("domain_summaries") Map<CustomerScenario, DomainMetric> domainSummaries,
            @JsonProperty("platform_summaries") Map<String, PlatformMetric> platformSummaries,
            @JsonProperty("confusion_matrix") int[][] confusionMatrix,
            @JsonProperty("confusion_labels") List<String> confusionLabels,
            @JsonProperty("all_gates_passed") boolean allGatesPassed,
            @JsonProperty("results") List<ScenarioExecutionResult> results
    ) {}
}

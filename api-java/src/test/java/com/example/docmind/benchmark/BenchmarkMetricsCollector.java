package com.example.docmind.benchmark;

import com.example.docmind.benchmark.BenchmarkModels.BenchmarkSummaryReport;
import com.example.docmind.benchmark.BenchmarkModels.DomainMetric;
import com.example.docmind.benchmark.BenchmarkModels.PlatformMetric;
import com.example.docmind.benchmark.BenchmarkModels.ScenarioExecutionResult;
import com.example.docmind.service.retrieval.CustomerScenario;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

/**
 * Bộ thu thập số liệu và tính toán thống kê phân tích đa chiều (Benchmark Metrics Collector)
 * đảm bảo an toàn đa luồng cho quá trình chạy benchmark 1.000 kịch bản.
 */
public class BenchmarkMetricsCollector {

    private static final List<String> CONFUSION_LABELS = List.of(
            "SALES", "SERVICE_SUPPORT", "WARRANTY_RETURN", "ORDER_BILLING", "GENERAL"
    );

    private final ConcurrentLinkedQueue<ScenarioExecutionResult> results = new ConcurrentLinkedQueue<>();

    public void record(ScenarioExecutionResult result) {
        if (result != null) {
            results.add(result);
        }
    }

    public List<ScenarioExecutionResult> getResultsSnapshot() {
        return new ArrayList<>(results);
    }

    public BenchmarkSummaryReport computeSummary(long totalDurationMs) {
        List<ScenarioExecutionResult> snapshot = new ArrayList<>(results);
        int total = snapshot.size();
        if (total == 0) {
            return new BenchmarkSummaryReport(
                    0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0.0,
                    0.0, 0, 0, 0, 0, 0, 0, totalDurationMs,
                    Map.of(), Map.of(), new int[5][5], CONFUSION_LABELS, false, List.of()
            );
        }

        long passedCount = snapshot.stream().filter(ScenarioExecutionResult::passed).count();
        long failedCount = total - passedCount;
        long classPassedCount = snapshot.stream().filter(ScenarioExecutionResult::classificationPassed).count();

        List<ScenarioExecutionResult> safetyCases = snapshot.stream()
                .filter(ScenarioExecutionResult::isSafetyCritical)
                .toList();
        long safetyPassedCount = safetyCases.stream().filter(ScenarioExecutionResult::safetyPassed).count();

        long citationPassedCount = snapshot.stream().filter(ScenarioExecutionResult::citationHygienePassed).count();
        long structuredCitationsPassedCount = snapshot.stream().filter(ScenarioExecutionResult::ragRelevancePassed).count();

        double passRatePct = round2((passedCount * 100.0) / total);
        double classificationAccPct = round2((classPassedCount * 100.0) / total);
        double safetyCompliancePct = safetyCases.isEmpty() ? 100.0 : round2((safetyPassedCount * 100.0) / safetyCases.size());
        double citationHygieneCompliancePct = round2((citationPassedCount * 100.0) / total);
        double structuredCitationsIntegrityPct = round2((structuredCitationsPassedCount * 100.0) / total);

        List<Long> latencies = snapshot.stream()
                .map(ScenarioExecutionResult::latencyMs)
                .sorted()
                .toList();

        double meanLatencyMs = round2(latencies.stream().mapToLong(Long::longValue).average().orElse(0.0));
        long minLatencyMs = latencies.isEmpty() ? 0 : latencies.get(0);
        long maxLatencyMs = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
        long p50LatencyMs = computePercentile(latencies, 50);
        long p90LatencyMs = computePercentile(latencies, 90);
        long p95LatencyMs = computePercentile(latencies, 95);
        long p99LatencyMs = computePercentile(latencies, 99);

        Map<CustomerScenario, DomainMetric> domainSummaries = computeDomainSummaries(snapshot);
        Map<String, PlatformMetric> platformSummaries = computePlatformSummaries(snapshot);
        int[][] confusionMatrix = computeConfusionMatrix(snapshot);

        boolean allGatesPassed = passRatePct >= 98.0
                && classificationAccPct >= 98.0
                && safetyCompliancePct >= 100.0
                && citationHygieneCompliancePct >= 100.0;

        return new BenchmarkSummaryReport(
                total,
                (int) passedCount,
                (int) failedCount,
                passRatePct,
                classificationAccPct,
                safetyCompliancePct,
                citationHygieneCompliancePct,
                structuredCitationsIntegrityPct,
                meanLatencyMs,
                minLatencyMs,
                p50LatencyMs,
                p90LatencyMs,
                p95LatencyMs,
                p99LatencyMs,
                maxLatencyMs,
                totalDurationMs,
                domainSummaries,
                platformSummaries,
                confusionMatrix,
                CONFUSION_LABELS,
                allGatesPassed,
                snapshot
        );
    }

    private Map<CustomerScenario, DomainMetric> computeDomainSummaries(List<ScenarioExecutionResult> list) {
        Map<CustomerScenario, List<ScenarioExecutionResult>> byDomain = list.stream()
                .collect(Collectors.groupingBy(ScenarioExecutionResult::expectedDomain));

        Map<CustomerScenario, DomainMetric> map = new LinkedHashMap<>();
        for (CustomerScenario sc : CustomerScenario.values()) {
            List<ScenarioExecutionResult> items = byDomain.getOrDefault(sc, List.of());
            int total = items.size();
            int passed = (int) items.stream().filter(ScenarioExecutionResult::passed).count();
            int failed = total - passed;
            double passRate = total == 0 ? 100.0 : round2((passed * 100.0) / total);
            double accuracy = total == 0 ? 100.0 : round2((items.stream().filter(ScenarioExecutionResult::classificationPassed).count() * 100.0) / total);

            List<ScenarioExecutionResult> scSafety = items.stream().filter(ScenarioExecutionResult::isSafetyCritical).toList();
            double safety = scSafety.isEmpty() ? 100.0 : round2((scSafety.stream().filter(ScenarioExecutionResult::safetyPassed).count() * 100.0) / scSafety.size());
            double cite = total == 0 ? 100.0 : round2((items.stream().filter(ScenarioExecutionResult::citationHygienePassed).count() * 100.0) / total);

            List<Long> lat = items.stream().map(ScenarioExecutionResult::latencyMs).sorted().toList();
            double avgLat = round2(lat.stream().mapToLong(Long::longValue).average().orElse(0.0));
            long p95Lat = computePercentile(lat, 95);

            map.put(sc, new DomainMetric(sc, total, passed, failed, passRate, accuracy, safety, cite, avgLat, p95Lat));
        }
        return map;
    }

    private Map<String, PlatformMetric> computePlatformSummaries(List<ScenarioExecutionResult> list) {
        Map<String, List<ScenarioExecutionResult>> byPlat = list.stream()
                .collect(Collectors.groupingBy(ScenarioExecutionResult::platform));

        Map<String, PlatformMetric> map = new LinkedHashMap<>();
        List<String> order = List.of(
                "facebook_messenger", "telegram", "webchat", "discord", "slack", "lark", "line"
        );
        for (String p : order) {
            List<ScenarioExecutionResult> items = byPlat.getOrDefault(p, List.of());
            int total = items.size();
            int passed = (int) items.stream().filter(ScenarioExecutionResult::passed).count();
            int failed = total - passed;
            double passRate = total == 0 ? 100.0 : round2((passed * 100.0) / total);
            double accuracy = total == 0 ? 100.0 : round2((items.stream().filter(ScenarioExecutionResult::classificationPassed).count() * 100.0) / total);

            List<Long> lat = items.stream().map(ScenarioExecutionResult::latencyMs).sorted().toList();
            double avgLat = round2(lat.stream().mapToLong(Long::longValue).average().orElse(0.0));
            long p95Lat = computePercentile(lat, 95);

            map.put(p, new PlatformMetric(p, total, passed, failed, passRate, accuracy, avgLat, p95Lat));
        }
        return map;
    }

    private int[][] computeConfusionMatrix(List<ScenarioExecutionResult> list) {
        int[][] matrix = new int[5][5];
        Map<CustomerScenario, Integer> indexMap = Map.of(
                CustomerScenario.SALES, 0,
                CustomerScenario.SERVICE_SUPPORT, 1,
                CustomerScenario.WARRANTY_RETURN, 2,
                CustomerScenario.ORDER_BILLING, 3,
                CustomerScenario.GENERAL, 4
        );

        for (ScenarioExecutionResult r : list) {
            Integer expectedIdx = indexMap.get(r.expectedDomain());
            Integer actualIdx = indexMap.get(r.actualDomain());
            if (expectedIdx != null && actualIdx != null) {
                matrix[expectedIdx][actualIdx]++;
            }
        }
        return matrix;
    }

    private static long computePercentile(List<Long> sortedList, int percentile) {
        if (sortedList == null || sortedList.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil((percentile / 100.0) * sortedList.size()) - 1;
        index = Math.max(0, Math.min(index, sortedList.size() - 1));
        return sortedList.get(index);
    }

    private static double round2(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}

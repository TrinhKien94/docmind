package com.example.docmind.benchmark;

import com.example.docmind.benchmark.BenchmarkModels.BenchmarkSummaryReport;
import com.example.docmind.benchmark.BenchmarkModels.ScenarioExecutionResult;
import com.example.docmind.service.retrieval.CustomerScenario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Empirical Stress Test Harness for BenchmarkMetricsCollector and multi-threaded execution.
 * Stress-tests concurrency thread-safety under 32 threads, high contention, and boundary conditions.
 */
class BenchmarkMetricsCollectorConcurrencyStressTest {

    private static final List<String> PLATFORMS = List.of(
            "facebook_messenger", "telegram", "webchat", "discord", "slack", "lark", "line"
    );

    @Test
    @DisplayName("Stress Test: 32 concurrent threads recording 3,200 results with barrier sync")
    void testConcurrentRecordingUnder32Threads() throws Exception {
        BenchmarkMetricsCollector collector = new BenchmarkMetricsCollector();
        int threadCount = 32;
        int itemsPerThread = 100;
        int totalExpected = threadCount * itemsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        CustomerScenario[] domains = CustomerScenario.values();

        for (int t = 0; t < threadCount; t++) {
            final int threadIdx = t;
            executor.submit(() -> {
                try {
                    barrier.await(); // Align all threads to start simultaneously
                    for (int i = 0; i < itemsPerThread; i++) {
                        CustomerScenario domain = domains[(threadIdx * itemsPerThread + i) % domains.length];
                        String platform = PLATFORMS.get(i % PLATFORMS.size());
                        long latency = (i + 1) * 5L;

                        ScenarioExecutionResult result = new ScenarioExecutionResult(
                                "SCEN-THREAD-" + threadIdx + "-" + i,
                                "Test Scenario " + i,
                                domain,
                                domain,
                                List.of(),
                                platform,
                                "User query " + i,
                                "Assistant reply " + i,
                                List.of(),
                                latency,
                                (i % 5 == 0),
                                (i % 5 == 0) ? "PREGNANCY_CONTRAINDICATION" : null,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                List.of(),
                                List.of(),
                                List.of()
                        );
                        collector.record(result);
                    }
                } catch (Exception e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        assertThat(completed).isTrue();

        List<ScenarioExecutionResult> snapshot = collector.getResultsSnapshot();
        assertThat(snapshot).hasSize(totalExpected);

        BenchmarkSummaryReport summary = collector.computeSummary(5000);
        assertThat(summary.totalScenarios()).isEqualTo(totalExpected);
        assertThat(summary.passedScenarios()).isEqualTo(totalExpected);
        assertThat(summary.failedScenarios()).isEqualTo(0);
        assertThat(summary.overallPassRatePct()).isEqualTo(100.0);
        assertThat(summary.classificationAccuracyPct()).isEqualTo(100.0);
        assertThat(summary.criticalSafetyCompliancePct()).isEqualTo(100.0);
        assertThat(summary.citationHygieneCompliancePct()).isEqualTo(100.0);

        // Verify all 5 domains have recorded items summing to totalExpected
        int domainSum = summary.domainSummaries().values().stream().mapToInt(BenchmarkModels.DomainMetric::total).sum();
        assertThat(domainSum).isEqualTo(totalExpected);

        // Verify all 7 platforms have recorded items summing to totalExpected
        int platformSum = summary.platformSummaries().values().stream().mapToInt(BenchmarkModels.PlatformMetric::total).sum();
        assertThat(platformSum).isEqualTo(totalExpected);

        // Verify confusion matrix sum equals totalExpected
        int matrixSum = 0;
        for (int[] row : summary.confusionMatrix()) {
            for (int val : row) {
                matrixSum += val;
            }
        }
        assertThat(matrixSum).isEqualTo(totalExpected);
    }

    @Test
    @DisplayName("Stress Test: Concurrent writes and reads without ConcurrentModificationException or races")
    void testConcurrentWritesAndReads() throws Exception {
        BenchmarkMetricsCollector collector = new BenchmarkMetricsCollector();
        int writerCount = 24;
        int readerCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(writerCount + readerCount);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicInteger writeCount = new AtomicInteger(0);
        AtomicInteger readCount = new AtomicInteger(0);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        // Readers continuously call computeSummary and getResultsSnapshot
        for (int r = 0; r < readerCount; r++) {
            executor.submit(() -> {
                try {
                    while (running.get()) {
                        BenchmarkSummaryReport report = collector.computeSummary(1000);
                        // Internal consistency of the report snapshot
                        assertThat(report.totalScenarios()).isEqualTo(report.results().size());
                        assertThat(report.passedScenarios() + report.failedScenarios()).isEqualTo(report.totalScenarios());
                        int domainSum = report.domainSummaries().values().stream().mapToInt(BenchmarkModels.DomainMetric::total).sum();
                        assertThat(domainSum).isEqualTo(report.totalScenarios());

                        List<ScenarioExecutionResult> snapshot = collector.getResultsSnapshot();
                        assertThat(snapshot.size()).isLessThanOrEqualTo(writerCount * 200);
                        readCount.incrementAndGet();
                        Thread.yield();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                }
            });
        }

        // Writers continuously record items
        CountDownLatch writersDone = new CountDownLatch(writerCount);
        CustomerScenario[] domains = CustomerScenario.values();
        for (int w = 0; w < writerCount; w++) {
            final int writerId = w;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < 200; i++) {
                        CustomerScenario domain = domains[i % domains.length];
                        ScenarioExecutionResult result = new ScenarioExecutionResult(
                                "SCEN-CW-" + writerId + "-" + i,
                                "Concurrent Scenario",
                                domain,
                                domain,
                                List.of(),
                                "webchat",
                                "query",
                                "reply",
                                List.of(),
                                50L + i,
                                false,
                                null,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                true,
                                List.of(),
                                List.of(),
                                List.of()
                        );
                        collector.record(result);
                        writeCount.incrementAndGet();
                    }
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    writersDone.countDown();
                }
            });
        }

        boolean finishedWriters = writersDone.await(20, TimeUnit.SECONDS);
        running.set(false);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertThat(finishedWriters).isTrue();
        assertThat(errors).isEmpty();
        assertThat(writeCount.get()).isEqualTo(writerCount * 200);
        assertThat(readCount.get()).isGreaterThan(0);

        BenchmarkSummaryReport finalReport = collector.computeSummary(2000);
        assertThat(finalReport.totalScenarios()).isEqualTo(writerCount * 200);
    }

    @Test
    @DisplayName("Edge Case Test: Empty collector, single element, boundary percentiles")
    void testBoundaryAndEdgeCases() {
        BenchmarkMetricsCollector emptyCollector = new BenchmarkMetricsCollector();

        // 1. Empty collector: total = 0
        BenchmarkSummaryReport emptyReport = emptyCollector.computeSummary(100);
        assertThat(emptyReport.totalScenarios()).isEqualTo(0);
        assertThat(emptyReport.passedScenarios()).isEqualTo(0);
        assertThat(emptyReport.failedScenarios()).isEqualTo(0);
        assertThat(emptyReport.overallPassRatePct()).isEqualTo(0.0);
        assertThat(emptyReport.meanLatencyMs()).isEqualTo(0.0);
        assertThat(emptyReport.p50LatencyMs()).isEqualTo(0);
        assertThat(emptyReport.p95LatencyMs()).isEqualTo(0);
        assertThat(emptyReport.p99LatencyMs()).isEqualTo(0);
        assertThat(emptyReport.allGatesPassed()).isFalse();

        // 2. Single element
        BenchmarkMetricsCollector singleCollector = new BenchmarkMetricsCollector();
        ScenarioExecutionResult single = new ScenarioExecutionResult(
                "SINGLE-001",
                "Single Test",
                CustomerScenario.SALES,
                CustomerScenario.SALES,
                List.of(),
                "facebook_messenger",
                "query",
                "reply",
                List.of(),
                250L,
                false,
                null,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                List.of(),
                List.of(),
                List.of()
        );
        singleCollector.record(single);
        BenchmarkSummaryReport singleReport = singleCollector.computeSummary(50);
        assertThat(singleReport.totalScenarios()).isEqualTo(1);
        assertThat(singleReport.passedScenarios()).isEqualTo(1);
        assertThat(singleReport.overallPassRatePct()).isEqualTo(100.0);
        assertThat(singleReport.minLatencyMs()).isEqualTo(250L);
        assertThat(singleReport.maxLatencyMs()).isEqualTo(250L);
        assertThat(singleReport.meanLatencyMs()).isEqualTo(250.0);
        assertThat(singleReport.p50LatencyMs()).isEqualTo(250L);
        assertThat(singleReport.p90LatencyMs()).isEqualTo(250L);
        assertThat(singleReport.p95LatencyMs()).isEqualTo(250L);
        assertThat(singleReport.p99LatencyMs()).isEqualTo(250L);

        // 3. Null record should be safely ignored
        singleCollector.record(null);
        assertThat(singleCollector.getResultsSnapshot()).hasSize(1);
    }
}

package com.example.docmind.service.evolution;

import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.EvolvedExampleInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.LearnedGuidelineInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.TextualGradientInfo;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.service.FileValidation;
import com.example.docmind.service.llm.LlmClient;
import com.example.docmind.service.retrieval.CustomerScenario;
import com.example.docmind.service.retrieval.ReciprocalRankFusion;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Động cơ Tự tiến hoá & Học hỏi sau các lần chat với khách hàng (EvoAgentX Self-Evolving Engine).
 *
 * <p>Thiết kế dựa trên 3 trụ cột cốt lõi của <b>EvoAgentX</b>:
 * <ol>
 *   <li><b>TextGrad Optimizer ({@code textgrad_optimizer.py})</b>: Đánh giá đa tiêu chí câu trả lời
 *       sau mỗi phiên chat (Loss Evaluation), tính toán đạo hàm văn bản ({@code Textual Gradient})
 *       và cập nhật các quy tắc hướng dẫn ({@link LearnedGuidelineInfo}) cho từng kịch bản.</li>
 *   <li><b>MIPRO Exemplar Bootstrapping ({@code mipro_optimizer.py})</b>: Tự động chọn lọc các cặp
 *       Hỏi-Đáp đạt điểm chất lượng cao hoặc được khách hàng đánh giá tốt để đưa vào kho mẫu
 *       Few-Shot tiến hoá ({@link EvolvedExampleInfo}).</li>
 *   <li><b>Long-Term Memory Deduplication ({@code long_term_memory.py})</b>: Chống trùng lặp tri thức
 *       bằng mã băm {@code SHA-256}, tăng điểm củng cố ({@code reinforcement_count}) cho các quy tắc
 *       được xác nhận nhiều lần, cắt tỉa theo ngưỡng hiệu quả và lưu trữ bền vững ra file JSON.</li>
 * </ol>
 */
@Service
public class CustomerSelfEvolutionService {

    private static final Logger log = LoggerFactory.getLogger(CustomerSelfEvolutionService.class);
    private static final Pattern EVOLVED_TAG_PATTERN = Pattern.compile(
            "(?is)<\\s*evolved_guideline\\s*>(.*?)<\\s*/\\s*evolved_guideline\\s*>"
    );
    private static final Pattern UNCLOSED_EVOLVED_TAG_PATTERN = Pattern.compile(
            "(?is)<\\s*evolved_guideline\\s*>(.+)$"
    );
    private static final Pattern CITATION_MARKER_PATTERN = Pattern.compile("\\[(\\d+)]");

    private final CustomerSupportProperties customerProps;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Object stateLock = new Object();

    private final AtomicInteger evolutionGeneration = new AtomicInteger(0);
    private final AtomicLong totalInteractions = new AtomicLong(0);
    private final AtomicLong totalFeedbackCount = new AtomicLong(0);
    private double cumulativeQualityScore = 0.0;

    /** Quy tắc đã học theo mã kịch bản (SALES, SERVICE_SUPPORT, WARRANTY_RETURN, ORDER_BILLING, GENERAL). */
    private final Map<String, List<LearnedGuidelineInfo>> learnedGuidelines = new ConcurrentHashMap<>();

    /** Các mẫu Few-Shot chất lượng cao đã chắt lọc theo kịch bản. */
    private final Map<String, List<EvolvedExampleInfo>> evolvedExamples = new ConcurrentHashMap<>();

    /** Lịch sử các lần tương tác gần nhất để phục vụ feedback theo interaction_id và tối ưu hoá theo lô. */
    private final Map<String, InteractionEpisode> episodeStore = new ConcurrentHashMap<>();
    private final Deque<String> episodeOrder = new ArrayDeque<>();

    /** Lịch sử các Textual Gradient gần nhất để quan sát và kiểm toán. */
    private final Deque<TextualGradientInfo> recentGradients = new ArrayDeque<>();

    @Autowired
    public CustomerSelfEvolutionService(CustomerSupportProperties customerProps, LlmClient llmClient) {
        this(customerProps, llmClient, new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT));
    }

    public CustomerSelfEvolutionService(CustomerSupportProperties customerProps,
                                        LlmClient llmClient,
                                        ObjectMapper objectMapper) {
        this.customerProps = customerProps != null ? customerProps : new CustomerSupportProperties();
        this.llmClient = llmClient;
        this.objectMapper = objectMapper != null
                ? objectMapper
                : new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        for (CustomerScenario sc : CustomerScenario.values()) {
            learnedGuidelines.put(sc.code(), new ArrayList<>());
            evolvedExamples.put(sc.code(), new ArrayList<>());
        }
        loadFromDiskIfEnabled();
    }

    /**
     * Lấy danh sách các quy tắc đã tiến hoá (Learned Guidelines) áp dụng cho kịch bản hiện tại
     * (kết hợp quy tắc chuyên biệt của kịch bản + quy tắc chung GENERAL).
     */
    public List<LearnedGuidelineInfo> getActiveGuidelinesForScenario(CustomerScenario scenario) {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo == null || !evo.isEnabled()) {
            return List.of();
        }
        String code = scenario != null ? scenario.code() : CustomerScenario.GENERAL.code();
        int maxInPrompt = Math.max(1, evo.getMaxGuidelinesInPrompt());

        synchronized (stateLock) {
            List<LearnedGuidelineInfo> combined = new ArrayList<>();
            List<LearnedGuidelineInfo> scenarioList = learnedGuidelines.getOrDefault(code, List.of());
            combined.addAll(scenarioList);

            if (!CustomerScenario.GENERAL.code().equals(code)) {
                List<LearnedGuidelineInfo> generalList =
                        learnedGuidelines.getOrDefault(CustomerScenario.GENERAL.code(), List.of());
                for (LearnedGuidelineInfo g : generalList) {
                    if (combined.stream().noneMatch(existing -> existing.contentHash().equals(g.contentHash()))) {
                        combined.add(g);
                    }
                }
            }

            return combined.stream()
                    .sorted(Comparator.comparingDouble(
                            (LearnedGuidelineInfo g) -> g.confidenceScore() + 0.05 * g.reinforcementCount()
                    ).reversed())
                    .limit(maxInPrompt)
                    .toList();
        }
    }

    /**
     * Lấy danh sách các mẫu hội thoại Few-Shot đã tiến hoá cho kịch bản hiện tại.
     */
    public List<EvolvedExampleInfo> getActiveExamplesForScenario(CustomerScenario scenario) {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo == null || !evo.isEnabled()) {
            return List.of();
        }
        String code = scenario != null ? scenario.code() : CustomerScenario.GENERAL.code();
        int maxInPrompt = Math.max(1, evo.getMaxFewShotExemplarsInPrompt());

        synchronized (stateLock) {
            List<EvolvedExampleInfo> list = evolvedExamples.getOrDefault(code, List.of());
            return list.stream()
                    .sorted(Comparator.comparingDouble(
                            (EvolvedExampleInfo e) -> e.qualityScore() + 0.03 * e.reinforcementCount()
                    ).reversed())
                    .limit(maxInPrompt)
                    .toList();
        }
    }

    /**
     * Ghi nhận phiên chat vừa hoàn tất và thực thi chiến lược tự tiến hoá học hỏi (nếu bật).
     * Thực hiện LLM reflection (nếu có) bên ngoài khoá bộ nhớ để không chặn các luồng chat đồng thời.
     *
     * @return mã {@code interaction_id} của lượt hội thoại này.
     */
    public String recordAndEvolveAfterChat(CustomerChatRequest request,
                                           CustomerScenario primaryScenario,
                                           List<CustomerScenario> secondaryScenarios,
                                           String answer,
                                           List<ReciprocalRankFusion.FusedChunk> fusedChunks,
                                           String modelName) {
        String interactionId = UUID.randomUUID().toString();
        String question = request != null ? request.resolvedQuestion() : "";
        CustomerScenario resolvedPrimary = primaryScenario != null ? primaryScenario : CustomerScenario.GENERAL;
        List<CustomerScenario> resolvedSecondaries = secondaryScenarios != null ? secondaryScenarios : List.of();
        String scenarioCode = resolvedPrimary.code();
        int chunkCount = fusedChunks != null ? fusedChunks.size() : 0;

        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        long currentCount = totalInteractions.incrementAndGet();

        // Phát hiện tín hiệu ngầm định từ khách hàng (hài lòng / phàn nàn / hỏi lại trong history hoặc câu hỏi hiện tại)
        ImplicitSignal implicitSignal = detectImplicitSignal(request);

        // Đánh giá chất lượng câu trả lời theo đa tiêu chí (EvoAgentX Loss Evaluation)
        EvaluationResult eval = evaluateInteraction(
                question,
                answer,
                resolvedPrimary,
                resolvedSecondaries,
                chunkCount,
                implicitSignal,
                null,
                null,
                null
        );

        InteractionEpisode episode = new InteractionEpisode(
                interactionId,
                question,
                answer != null ? answer : "",
                scenarioCode,
                resolvedSecondaries.stream().map(CustomerScenario::code).toList(),
                chunkCount,
                modelName != null ? modelName : "unknown",
                eval.qualityScore(),
                OffsetDateTime.now().toString()
        );

        boolean shouldEvolve = evo != null && evo.isEnabled() && evo.isAutoEvolveAfterChat();
        boolean shouldCallLlmReflection = shouldEvolve
                && evo.isLlmReflectionEnabled()
                && ((evo.getLlmReflectionEveryNInteractions() > 0
                        && (currentCount % evo.getLlmReflectionEveryNInteractions() == 0))
                || implicitSignal == ImplicitSignal.NEGATIVE
                || eval.qualityScore() < evo.getLowScoreThresholdForCorrection());

        String precomputedLlmGuideline = null;
        if (shouldCallLlmReflection && llmClient != null
                && needsGuidelineEvolution(resolvedPrimary, resolvedSecondaries, eval, implicitSignal, null)) {
            precomputedLlmGuideline = tryGenerateGuidelineViaLlm(episode, eval, null);
        }

        synchronized (stateLock) {
            cumulativeQualityScore += eval.qualityScore();
            storeEpisode(episode);

            if (shouldEvolve) {
                applyEvolutionStepWithCandidate(
                        episode,
                        eval,
                        implicitSignal,
                        null,
                        shouldCallLlmReflection,
                        precomputedLlmGuideline
                );
                persistToDiskIfEnabled();
            }
        }

        return interactionId;
    }

    /**
     * Tiếp nhận phản hồi tường minh của khách hàng (POST /api/customer-chat/feedback)
     * và chạy ngay bước cập nhật đạo hàm văn bản (TextGrad step) + cập nhật bộ nhớ dài hạn.
     */
    public CustomerEvolutionStatusResponse recordFeedbackAndEvolve(CustomerFeedbackRequest feedbackReq) {
        if (feedbackReq == null || !feedbackReq.isTargetProvided()) {
            throw new IllegalArgumentException("Phản hồi khách hàng phải có interaction_id hoặc question");
        }
        totalFeedbackCount.incrementAndGet();

        InteractionEpisode existingEpisode = null;
        if (feedbackReq.interactionId() != null && !feedbackReq.interactionId().isBlank()) {
            synchronized (stateLock) {
                existingEpisode = episodeStore.get(feedbackReq.interactionId().strip());
            }
        }

        String question = (feedbackReq.question() != null && !feedbackReq.question().isBlank())
                ? feedbackReq.question().strip()
                : (existingEpisode != null ? existingEpisode.question() : "");
        String answer = (feedbackReq.answer() != null && !feedbackReq.answer().isBlank())
                ? feedbackReq.answer().strip()
                : (existingEpisode != null ? existingEpisode.answer() : "");
        CustomerScenario scenario = CustomerScenario.resolve(
                feedbackReq.scenario() != null
                        ? feedbackReq.scenario()
                        : (existingEpisode != null ? existingEpisode.scenario() : null),
                question,
                customerProps
        );
        List<CustomerScenario> secondaries = CustomerScenario.detectSecondaryScenarios(question, scenario, customerProps);
        int chunkCount = existingEpisode != null ? existingEpisode.retrievedChunkCount() : 0;
        String interactionId = existingEpisode != null
                ? existingEpisode.interactionId()
                : (feedbackReq.interactionId() != null && !feedbackReq.interactionId().isBlank()
                        ? feedbackReq.interactionId().strip()
                        : UUID.randomUUID().toString());

        EvaluationResult eval = evaluateInteraction(
                question,
                answer,
                scenario,
                secondaries,
                chunkCount,
                ImplicitSignal.NEUTRAL,
                feedbackReq.rating(),
                feedbackReq.helpful(),
                feedbackReq.feedback()
        );

        InteractionEpisode updatedEpisode = new InteractionEpisode(
                interactionId,
                question,
                answer,
                scenario.code(),
                secondaries.stream().map(CustomerScenario::code).toList(),
                chunkCount,
                existingEpisode != null ? existingEpisode.modelName() : "feedback-submitted",
                eval.qualityScore(),
                OffsetDateTime.now().toString()
        );

        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        boolean isNegativeFeedback = isNegativeExplicitFeedback(feedbackReq);
        boolean useLlm = evo != null && evo.isEnabled() && (evo.isLlmReflectionEnabled()
                || (isNegativeFeedback && evo.isLlmReflectionOnNegativeFeedback()));

        String precomputedLlmGuideline = null;
        if (useLlm && llmClient != null
                && needsGuidelineEvolution(scenario, secondaries, eval, ImplicitSignal.NEUTRAL, feedbackReq)) {
            precomputedLlmGuideline = tryGenerateGuidelineViaLlm(updatedEpisode, eval, feedbackReq);
        }

        synchronized (stateLock) {
            InteractionEpisode currentInStore = episodeStore.get(interactionId);
            if (currentInStore != null) {
                cumulativeQualityScore = Math.max(0.0,
                        cumulativeQualityScore + (eval.qualityScore() - currentInStore.qualityScore()));
                episodeStore.put(interactionId, updatedEpisode);
            } else {
                totalInteractions.incrementAndGet();
                cumulativeQualityScore += eval.qualityScore();
                storeEpisode(updatedEpisode);
            }

            if (evo == null || evo.isEnabled()) {
                applyEvolutionStepWithCandidate(
                        updatedEpisode,
                        eval,
                        ImplicitSignal.NEUTRAL,
                        feedbackReq,
                        useLlm,
                        precomputedLlmGuideline
                );
                persistToDiskIfEnabled();
            }
            return getStatus();
        }
    }

    /**
     * Kích hoạt chủ động 1 chu kỳ tối ưu hoá EvoAgentX TextGrad + MIPRO trên các tương tác gần đây.
     */
    public CustomerEvolutionStatusResponse triggerEvolutionCycle(String optionalScenario) {
        CustomerScenario targetScenario = (optionalScenario != null && !optionalScenario.isBlank())
                ? CustomerScenario.resolve(optionalScenario, "", customerProps)
                : null;

        List<InteractionEpisode> episodesSnapshot;
        synchronized (stateLock) {
            episodesSnapshot = episodeOrder.stream()
                    .map(episodeStore::get)
                    .filter(ep -> ep != null && (targetScenario == null || targetScenario.code().equals(ep.scenario())))
                    .toList();

            if (episodesSnapshot.isEmpty()) {
                // Nếu chưa có lịch sử tương tác, khởi tạo quy tắc nền tảng ban đầu cho kịch bản mục tiêu
                CustomerScenario sc = targetScenario != null ? targetScenario : CustomerScenario.SALES;
                String seedRule = synthesizeDeterministicGuideline(
                        sc,
                        List.of(),
                        "Khách hàng cần thông tin tư vấn rõ ràng, báo giá minh bạch và hướng dẫn từng bước.",
                        null,
                        true
                );
                upsertGuideline(sc.code(), seedRule, "EvoAgentX proactive baseline initialization", 0.85);
                int gen = evolutionGeneration.incrementAndGet();
                recordGradient(new TextualGradientInfo(
                        UUID.randomUUID().toString(),
                        sc.code(),
                        "(Proactive Evolution Cycle)",
                        0.85,
                        "Khởi tạo chu kỳ tiến hoá chủ động khi chưa có phiên chat trong bộ nhớ.",
                        "Củng cố quy tắc tư vấn trọng tâm cho kịch bản " + sc.code(),
                        "EVOLVED_GUIDELINE_UPSERT (gen=" + gen + ")",
                        OffsetDateTime.now().toString()
                ));
                persistToDiskIfEnabled();
                return getStatus();
            }
        }

        // Duyệt tối đa 5 tương tác gần nhất để phản tư sâu bằng TextGrad (chạy LLM ngoài lock)
        int startIdx = Math.max(0, episodesSnapshot.size() - 5);
        List<InteractionEpisode> batch = episodesSnapshot.subList(startIdx, episodesSnapshot.size());
        record PrecomputedStep(InteractionEpisode episode, EvaluationResult eval, String llmGuideline) {}
        List<PrecomputedStep> precomputedSteps = new ArrayList<>(batch.size());

        for (InteractionEpisode ep : batch) {
            CustomerScenario sc = CustomerScenario.resolve(ep.scenario(), ep.question(), customerProps);
            List<CustomerScenario> secs = CustomerScenario.detectSecondaryScenarios(ep.question(), sc, customerProps);
            EvaluationResult eval = evaluateInteraction(
                    ep.question(), ep.answer(), sc, secs, ep.retrievedChunkCount(),
                    ImplicitSignal.NEUTRAL, null, null, null
            );
            String llmGuideline = null;
            if (llmClient != null && needsGuidelineEvolution(sc, secs, eval, ImplicitSignal.NEUTRAL, null)) {
                llmGuideline = tryGenerateGuidelineViaLlm(ep, eval, null);
            }
            precomputedSteps.add(new PrecomputedStep(ep, eval, llmGuideline));
        }

        synchronized (stateLock) {
            for (PrecomputedStep step : precomputedSteps) {
                applyEvolutionStepWithCandidate(
                        step.episode(), step.eval(), ImplicitSignal.NEUTRAL, null, true, step.llmGuideline());
            }
            persistToDiskIfEnabled();
            return getStatus();
        }
    }

    /**
     * Đặt lại (Reset) bộ nhớ tiến hoá về trạng thái ban đầu.
     */
    public CustomerEvolutionStatusResponse resetEvolutionMemory() {
        synchronized (stateLock) {
            evolutionGeneration.set(0);
            totalInteractions.set(0);
            totalFeedbackCount.set(0);
            cumulativeQualityScore = 0.0;
            for (CustomerScenario sc : CustomerScenario.values()) {
                learnedGuidelines.put(sc.code(), new ArrayList<>());
                evolvedExamples.put(sc.code(), new ArrayList<>());
            }
            episodeStore.clear();
            episodeOrder.clear();
            recentGradients.clear();
            persistToDiskIfEnabled();
            return getStatus();
        }
    }

    /**
     * Trả về trạng thái hiện tại của động cơ tự tiến hoá EvoAgentX.
     */
    public CustomerEvolutionStatusResponse getStatus() {
        synchronized (stateLock) {
            CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
            long interactions = totalInteractions.get();
            double avgScore = interactions > 0
                    ? Math.round(Math.max(0.0, Math.min(1.0, cumulativeQualityScore / interactions)) * 1000.0) / 1000.0
                    : 0.0;

            Map<String, List<LearnedGuidelineInfo>> guidelinesCopy = new LinkedHashMap<>();
            for (CustomerScenario sc : CustomerScenario.values()) {
                guidelinesCopy.put(sc.code(), List.copyOf(learnedGuidelines.getOrDefault(sc.code(), List.of())));
            }

            Map<String, List<EvolvedExampleInfo>> examplesCopy = new LinkedHashMap<>();
            for (CustomerScenario sc : CustomerScenario.values()) {
                examplesCopy.put(sc.code(), List.copyOf(evolvedExamples.getOrDefault(sc.code(), List.of())));
            }

            return new CustomerEvolutionStatusResponse(
                    evo == null || evo.isEnabled(),
                    evo != null ? evo.getStrategy() : "evoagentx-textgrad-mipro",
                    evolutionGeneration.get(),
                    interactions,
                    totalFeedbackCount.get(),
                    avgScore,
                    guidelinesCopy,
                    examplesCopy,
                    List.copyOf(recentGradients)
            );
        }
    }

    public int getCurrentGeneration() {
        return evolutionGeneration.get();
    }

    // =========================================================================
    // Nội bộ: Đánh giá đa tiêu chí (Loss), tính Textual Gradient & Cập nhật Bộ nhớ
    // =========================================================================

    private static boolean isNegativeExplicitFeedback(CustomerFeedbackRequest explicitFeedback) {
        if (explicitFeedback == null) {
            return false;
        }
        if (Boolean.FALSE.equals(explicitFeedback.helpful())) {
            return true;
        }
        if (explicitFeedback.rating() != null && explicitFeedback.rating() <= 3) {
            return true;
        }
        boolean isExplicitlyPositive = Boolean.TRUE.equals(explicitFeedback.helpful())
                || (explicitFeedback.rating() != null && explicitFeedback.rating() >= 4);
        return !isExplicitlyPositive
                && explicitFeedback.feedback() != null
                && !explicitFeedback.feedback().isBlank();
    }

    private boolean needsGuidelineEvolution(CustomerScenario scenario,
                                            List<CustomerScenario> secondaries,
                                            EvaluationResult eval,
                                            ImplicitSignal implicitSignal,
                                            CustomerFeedbackRequest explicitFeedback) {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        double lowThreshold = evo != null ? evo.getLowScoreThresholdForCorrection() : 0.78;
        boolean hasExplicitCorrection = isNegativeExplicitFeedback(explicitFeedback);
        boolean emptyForScenario;
        synchronized (stateLock) {
            emptyForScenario = learnedGuidelines.getOrDefault(scenario.code(), List.of()).isEmpty();
        }
        return eval.qualityScore() < lowThreshold
                || implicitSignal == ImplicitSignal.NEGATIVE
                || hasExplicitCorrection
                || (secondaries != null && !secondaries.isEmpty())
                || emptyForScenario;
    }

    private void applyEvolutionStepWithCandidate(InteractionEpisode episode,
                                                 EvaluationResult eval,
                                                 ImplicitSignal implicitSignal,
                                                 CustomerFeedbackRequest explicitFeedback,
                                                 boolean invokeLlmOptimizer,
                                                 String precomputedLlmGuideline) {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        CustomerScenario scenario = CustomerScenario.resolve(episode.scenario(), episode.question(), customerProps);
        List<CustomerScenario> secondaries = CustomerScenario.detectSecondaryScenarios(
                episode.question(), scenario, customerProps);

        String appliedAction = "MONITORED";
        double lowThreshold = evo != null ? evo.getLowScoreThresholdForCorrection() : 0.78;
        double exemplarThreshold = evo != null ? evo.getMinScoreForExemplar() : 0.80;

        boolean hasExplicitCorrection = isNegativeExplicitFeedback(explicitFeedback);

        boolean needsGuideline = eval.qualityScore() < lowThreshold
                || implicitSignal == ImplicitSignal.NEGATIVE
                || hasExplicitCorrection
                || !secondaries.isEmpty()
                || learnedGuidelines.getOrDefault(scenario.code(), List.of()).isEmpty();

        if (needsGuideline) {
            String candidateGuideline = precomputedLlmGuideline;
            if ((candidateGuideline == null || candidateGuideline.isBlank())
                    && invokeLlmOptimizer && llmClient != null) {
                candidateGuideline = tryGenerateGuidelineViaLlm(episode, eval, explicitFeedback);
            }
            if (candidateGuideline == null || candidateGuideline.isBlank()) {
                candidateGuideline = synthesizeDeterministicGuideline(
                        scenario,
                        secondaries,
                        eval.textualGradient(),
                        hasExplicitCorrection && explicitFeedback != null ? explicitFeedback.feedback() : null,
                        episode.retrievedChunkCount() > 0
                );
            }
            double guidelineConfidence = Math.max(0.72, Math.min(0.96, eval.qualityScore() + 0.12));
            upsertGuideline(scenario.code(), candidateGuideline, eval.textualGradient(), guidelineConfidence);
            int gen = evolutionGeneration.incrementAndGet();
            appliedAction = "EVOLVED_GUIDELINE_UPSERT (gen=" + gen + ")";
        }

        // Nếu nhận phản hồi tiêu cực hoặc điểm chất lượng bị hạ thấp, loại bỏ mẫu Few-Shot cũ (nếu đã trót lưu)
        if (hasExplicitCorrection
                || implicitSignal == ImplicitSignal.NEGATIVE
                || (explicitFeedback != null && eval.qualityScore() < exemplarThreshold)) {
            if (evictExemplarIfPresent(scenario.code(), episode.question())) {
                int gen = evolutionGeneration.incrementAndGet();
                appliedAction = appliedAction.equals("MONITORED")
                        ? "EVICTED_EXEMPLAR (gen=" + gen + ")"
                        : appliedAction + " + EVICTED_EXEMPLAR";
            }
        }

        // MIPRO Exemplar Bootstrapping: Nếu câu trả lời chất lượng cao hoặc khách hàng khen ngợi -> lưu mẫu Few-Shot
        boolean eligibleForExemplar = (eval.qualityScore() >= exemplarThreshold
                || implicitSignal == ImplicitSignal.POSITIVE
                || (explicitFeedback != null && ((explicitFeedback.rating() != null && explicitFeedback.rating() >= 4)
                || Boolean.TRUE.equals(explicitFeedback.helpful()))))
                && !hasExplicitCorrection
                && implicitSignal != ImplicitSignal.NEGATIVE
                && episode.question() != null && !episode.question().isBlank()
                && episode.answer() != null && !episode.answer().isBlank()
                && !episode.answer().startsWith("[stub-llm]");

        if (eligibleForExemplar) {
            upsertExemplar(scenario.code(), episode.question(), episode.answer(), eval.qualityScore());
            int gen = evolutionGeneration.incrementAndGet();
            appliedAction = appliedAction.equals("MONITORED")
                    ? "BOOTSTRAPPED_EXEMPLAR (gen=" + gen + ")"
                    : appliedAction + " + BOOTSTRAPPED_EXEMPLAR";
        }

        recordGradient(new TextualGradientInfo(
                episode.interactionId(),
                scenario.code(),
                truncate(episode.question(), 120),
                eval.qualityScore(),
                eval.lossCritique(),
                eval.textualGradient(),
                appliedAction,
                OffsetDateTime.now().toString()
        ));
    }

    private EvaluationResult evaluateInteraction(String question,
                                                 String answer,
                                                 CustomerScenario scenario,
                                                 List<CustomerScenario> secondaryScenarios,
                                                 int retrievedChunkCount,
                                                 ImplicitSignal implicitSignal,
                                                 Integer explicitRating,
                                                 Boolean explicitHelpful,
                                                 String explicitComment) {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        String safeAnswer = answer != null ? answer.strip() : "";
        String normAnswer = CustomerScenario.normalizeWordsPadded(safeAnswer);

        List<String> critiques = new ArrayList<>();
        List<String> gradients = new ArrayList<>();

        // 1. Thái độ, xưng hô & sự đồng cảm (Salutation & Tone) — đọc từ khoá từ cấu hình
        double toneScore = 0.6;
        List<String> salutationKeywords = (evo != null && evo.getSalutationKeywords() != null
                && !evo.getSalutationKeywords().isEmpty())
                ? evo.getSalutationKeywords()
                : List.of("da", "chao", "cam on", "anh chi", "quy khach", "em xin");
        boolean hasPoliteGreeting = containsAnyNormalizedKeyword(normAnswer, salutationKeywords);
        if (hasPoliteGreeting) {
            toneScore = 1.0;
        } else {
            critiques.add("Thiếu lời chào hoặc cách xưng hô thân thiện ('Dạ', 'Anh/Chị').");
            gradients.add("Luôn mở đầu bằng lời chào lịch sự ('Dạ em chào Anh/Chị') và giữ giọng điệu tận tâm.");
        }
        if (scenario == CustomerScenario.WARRANTY_RETURN
                && !(normAnswer.contains(" bao hanh ") || normAnswer.contains(" doi tra ")
                || normAnswer.contains(" hoan tien ") || normAnswer.contains(" 1 doi 1 ")
                || normAnswer.contains(" ho tro ") || normAnswer.contains(" kich ung "))) {
            toneScore = Math.max(0.4, toneScore - 0.3);
            critiques.add("Kịch bản bảo hành/đổi trả chưa nhấn mạnh cam kết hỗ trợ quyền lợi khách hàng.");
            gradients.add("Khi khách hỏi bảo hành/đổi trả/kích ứng, cần thể hiện sự đồng cảm và nêu rõ thời hạn đổi trả 7 ngày, lỗi vận chuyển/kích ứng 14 ngày.");
        }

        // 2. Độ chính xác & Trích dẫn tài liệu (Grounding & Citations)
        double groundingScore = 0.85;
        if (retrievedChunkCount > 0) {
            Matcher m = CITATION_MARKER_PATTERN.matcher(safeAnswer);
            if (m.find()) {
                groundingScore = 1.0;
            } else {
                groundingScore = 0.55;
                critiques.add("Có " + retrievedChunkCount + " đoạn tài liệu RAG nhưng câu trả lời chưa đính kèm trích dẫn [n].");
                gradients.add("Bắt buộc trích dẫn số hiệu đoạn tài liệu [1], [2]... ngay sau thông tin giá bán hoặc thông số lấy từ tài liệu.");
            }
        }

        // 3. Độ đầy đủ & Đa ý định (Completeness & Multi-Intent coverage)
        int minChars = evo != null && evo.getMinCompleteAnswerChars() > 0 ? evo.getMinCompleteAnswerChars() : 80;
        double completenessScore = safeAnswer.length() >= minChars ? 0.95 : 0.60;
        if (safeAnswer.length() < minChars) {
            critiques.add("Câu trả lời hơi ngắn (" + safeAnswer.length() + " ký tự), chưa giải thích chi tiết.");
            gradients.add("Cung cấp giải thích đầy đủ quyền lợi, quy trình từng bước và thông tin chính sách liên quan.");
        }
        if (secondaryScenarios != null && !secondaryScenarios.isEmpty()) {
            for (CustomerScenario sec : secondaryScenarios) {
                if (sec == CustomerScenario.WARRANTY_RETURN
                        && !(normAnswer.contains(" bao hanh ") || normAnswer.contains(" doi tra ")
                        || normAnswer.contains(" hoan tien ") || normAnswer.contains(" 1 doi 1 ")
                        || normAnswer.contains(" kich ung ") || normAnswer.contains(" chinh hang "))) {
                    completenessScore = Math.max(0.5, completenessScore - 0.25);
                    critiques.add("Câu hỏi đa ý định có hỏi thêm về Bảo hành/Đổi trả/Xác thực nhưng câu trả lời chưa đề cập đầy đủ.");
                    gradients.add("Với câu hỏi kết hợp Mua hàng và Bảo hành/Đổi trả, phải trả lời trọn vẹn cả báo giá sản phẩm/dịch vụ lẫn chính sách đổi trả 7-14 ngày.");
                } else if (sec == CustomerScenario.ORDER_BILLING
                        && !(normAnswer.contains(" thanh toan ") || normAnswer.contains(" hoa don ")
                        || normAnswer.contains(" vat ") || normAnswer.contains(" giao hang ")
                        || normAnswer.contains(" van chuyen ") || normAnswer.contains(" hoa toc ")
                        || normAnswer.contains(" tra gop ") || normAnswer.contains(" hop qua "))) {
                    completenessScore = Math.max(0.5, completenessScore - 0.25);
                    critiques.add("Câu hỏi đa ý định có hỏi thêm về Thanh toán/Hóa đơn/Vận chuyển nhưng chưa giải đáp.");
                    gradients.add("Khi khách hàng hỏi kèm thủ tục thanh toán, đóng gói quà hoặc vận chuyển/VAT, cần nêu rõ kênh thanh toán, giao hỏa tốc 2h và xuất hóa đơn VAT điện tử.");
                } else if (sec == CustomerScenario.SERVICE_SUPPORT
                        && !(normAnswer.contains(" buoc ") || normAnswer.contains(" huong dan ")
                        || normAnswer.contains(" cach dung ") || normAnswer.contains(" routine ")
                        || normAnswer.contains(" batch ") || normAnswer.contains(" mang thai ")
                        || normAnswer.contains(" refill ") || normAnswer.contains(" khac ten "))) {
                    completenessScore = Math.max(0.5, completenessScore - 0.20);
                    critiques.add("Câu hỏi đa ý định có hỏi thêm về Hướng dẫn sử dụng/Routine/Kỹ thuật nhưng chưa hướng dẫn chi tiết.");
                    gradients.add("Khi khách hàng hỏi kèm cách sử dụng, chu trình skincare hoặc kiểm tra mã sản phẩm, cần trình bày hướng dẫn từng bước rõ ràng.");
                }
            }
        }

        // 3b. Kiểm tra an toàn chống chỉ định thai kỳ (Pregnancy & Contraindication Safety Check)
        if (evo == null || evo.isPregnancySafetyCheckEnabled()) {
            String normQuestion = CustomerScenario.normalizeWordsPadded(question);
            List<String> pregTriggers = (evo != null && evo.getPregnancyTriggerKeywords() != null
                    && !evo.getPregnancyTriggerKeywords().isEmpty())
                    ? evo.getPregnancyTriggerKeywords()
                    : List.of("mang thai", "ba bau", "me bau", "thai ky", "cho con bu");
            List<String> pregSafetyKeywords = (evo != null && evo.getPregnancySafetyRequiredKeywords() != null
                    && !evo.getPregnancySafetyRequiredKeywords().isEmpty())
                    ? evo.getPregnancySafetyRequiredKeywords()
                    : List.of("retishot", "retinol", "chong chi dinh", "khong su dung", "khong dung", "tranh", "an toan", "lanh tinh");

            if (containsAnyNormalizedKeyword(normQuestion, pregTriggers)
                    && !containsAnyNormalizedKeyword(normAnswer, pregSafetyKeywords)) {
                toneScore = Math.min(toneScore, 0.45);
                groundingScore = Math.min(groundingScore, 0.35);
                completenessScore = Math.max(0.20, completenessScore - 0.50);
                critiques.add("Khách hàng đang mang thai/thai kỳ nhưng câu trả lời chưa cảnh báo chống chỉ định Retinol (Capture Totale Retishot) và xác nhận độ an toàn.");
                gradients.add("Khi tư vấn mỹ phẩm cho phụ nữ mang thai (thai kỳ) hoặc cho con bú, bắt buộc cảnh báo tuyệt đối không dùng Capture Totale Retishot (0.1% Retinol) và chỉ gợi ý dòng lành tính (Capture Totale Le Sérum, Dior Prestige, La Mousse OFF/ON).");
            }
        }

        // 4. Tính hành động & Hướng dẫn tiếp theo (Actionability / Call-to-Action) — đọc từ khoá từ cấu hình
        double actionScore = 0.70;
        List<String> actionKeywords = (evo != null && evo.getActionabilityKeywords() != null
                && !evo.getActionabilityKeywords().isEmpty())
                ? evo.getActionabilityKeywords()
                : List.of("hotline", "email", "lien he", "huong dan", "dang ky", "dung thu", "bao gia", "ho tro");
        boolean hasActionOrContact = containsAnyNormalizedKeyword(normAnswer, actionKeywords)
                || safeAnswer.contains("?");
        if (hasActionOrContact) {
            actionScore = 0.95;
        } else {
            critiques.add("Thiếu câu gợi mở hành động (Call-to-Action) hoặc thông tin liên hệ hỗ trợ tiếp theo.");
            gradients.add("Kết thúc câu trả lời bằng lời gợi ý bước tiếp theo (đăng ký dùng thử, nhận báo giá hoặc liên hệ Hotline/Email).");
        }

        double wTone = evo != null ? evo.getWeightSalutationAndTone() : 0.25;
        double wGround = evo != null ? evo.getWeightGroundingAndCitation() : 0.25;
        double wComp = evo != null ? evo.getWeightCompletenessAndLength() : 0.25;
        double wAct = evo != null ? evo.getWeightActionabilityAndContact() : 0.25;
        double totalWeight = (wTone + wGround + wComp + wAct);
        if (totalWeight <= 0) {
            totalWeight = 1.0;
        }

        double score = (wTone * toneScore + wGround * groundingScore + wComp * completenessScore + wAct * actionScore)
                / totalWeight;

        // Điều chỉnh theo tín hiệu ngầm định hoặc phản hồi tường minh của khách hàng
        if (implicitSignal == ImplicitSignal.POSITIVE) {
            score = Math.min(1.0, score + 0.08);
            critiques.add("Khách hàng thể hiện tín hiệu hài lòng trong hội thoại.");
        } else if (implicitSignal == ImplicitSignal.NEGATIVE) {
            score = Math.max(0.2, score - 0.22);
            critiques.add("Khách hàng có tín hiệu chưa hài lòng hoặc phải hỏi lại trong hội thoại.");
            gradients.add("Đi thẳng vào trọng tâm câu hỏi của khách hàng, tránh trả lời chung chung và xác nhận lại đúng nhu cầu.");
        }

        if (explicitRating != null) {
            double ratingNormalized = Math.max(1, Math.min(5, explicitRating)) / 5.0;
            score = 0.4 * score + 0.6 * ratingNormalized;
            critiques.add("Khách hàng đánh giá trực tiếp: " + explicitRating + "/5 sao.");
        }
        if (explicitHelpful != null) {
            score = explicitHelpful ? Math.max(score, 0.88) : Math.min(score, 0.45);
            critiques.add("Phản hồi hữu ích (helpful): " + explicitHelpful + ".");
        }
        if (explicitComment != null && !explicitComment.isBlank()) {
            String cleanComment = sanitizeRuleText(explicitComment);
            critiques.add("Góp ý trực tiếp từ khách hàng: \"" + cleanComment + "\".");
            gradients.add("Khắc phục theo góp ý của khách hàng (" + scenario.title(customerProps) + "): " + cleanComment);
        }

        score = Math.round(Math.max(0.0, Math.min(1.0, score)) * 1000.0) / 1000.0;

        String lossCritique = critiques.isEmpty()
                ? "Câu trả lời đạt chuẩn tư vấn khách hàng trên cả 4 tiêu chí (Thái độ, Trích dẫn, Độ đầy đủ, Call-to-Action)."
                : String.join(" ", critiques);
        String textualGradient = gradients.isEmpty()
                ? "Duy trì phong cách tư vấn lịch sự, trích dẫn chính xác [n] và gợi mở bước tiếp theo cho kịch bản " + scenario.code() + "."
                : String.join(" ", gradients);

        return new EvaluationResult(score, lossCritique, textualGradient);
    }

    private ImplicitSignal detectImplicitSignal(CustomerChatRequest request) {
        if (request == null) {
            return ImplicitSignal.NEUTRAL;
        }
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        List<String> posKeywords = (evo != null && evo.getPositiveSignalKeywords() != null)
                ? evo.getPositiveSignalKeywords()
                : List.of("cam on", "rat ro", "huu ich", "tuyet voi", "chot don", "hai long");
        List<String> negKeywords = (evo != null && evo.getNegativeSignalKeywords() != null)
                ? evo.getNegativeSignalKeywords()
                : List.of("chua dung", "sai roi", "khong phai", "chua hieu", "chua ro", "y toi la", "hoi lai");

        String paddedQuestion = CustomerScenario.normalizeWordsPadded(request.resolvedQuestion());
        if (containsAnyNormalizedKeyword(paddedQuestion, negKeywords)) {
            return ImplicitSignal.NEGATIVE;
        }
        if (containsAnyNormalizedKeyword(paddedQuestion, posKeywords)) {
            return ImplicitSignal.POSITIVE;
        }

        // Kiểm tra thêm lịch sử hội thoại: phát hiện khách hàng phải lặp lại cùng câu hỏi hoặc phàn nàn ở lượt gần nhất
        if (request.history() != null && !request.history().isEmpty()) {
            String normCurrent = paddedQuestion.strip();
            List<String> currentTokens = normCurrent.isEmpty() ? List.of() : List.of(normCurrent.split("\\s+"));

            for (int i = request.history().size() - 1; i >= 0; i--) {
                CustomerChatRequest.ConversationTurn turn = request.history().get(i);
                if (turn == null || turn.content() == null || turn.content().isBlank()) {
                    continue;
                }
                String role = turn.role() != null ? turn.role().strip().toLowerCase(Locale.ROOT) : "user";
                boolean isAssistant = customerProps.getAssistantRoleAliases() != null
                        && customerProps.getAssistantRoleAliases().stream()
                        .anyMatch(a -> a != null && role.equals(a.strip().toLowerCase(Locale.ROOT)));
                if (isAssistant) {
                    continue;
                }
                String paddedHistTurn = CustomerScenario.normalizeWordsPadded(turn.content());
                String normHist = paddedHistTurn.strip();
                if (currentTokens.size() >= 3 && !normHist.isEmpty()) {
                    List<String> histTokens = List.of(normHist.split("\\s+"));
                    long common = currentTokens.stream().filter(histTokens::contains).count();
                    int denom = Math.max(currentTokens.size(), histTokens.size());
                    if (denom > 0 && ((double) common / denom) >= 0.75) {
                        return ImplicitSignal.NEGATIVE;
                    }
                }
                if (containsAnyNormalizedKeyword(paddedHistTurn, negKeywords)) {
                    return ImplicitSignal.NEGATIVE;
                }
                break; // chỉ xét lượt khách hàng gần nhất đối với từ khoá cảm xúc
            }
        }
        return ImplicitSignal.NEUTRAL;
    }

    private static boolean containsAnyNormalizedKeyword(String paddedHaystack, List<String> keywords) {
        if (paddedHaystack == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank()) {
                String normKw = CustomerScenario.normalizeWordsPadded(kw).strip();
                if (!normKw.isEmpty() && paddedHaystack.contains(" " + normKw + " ")) {
                    return true;
                }
            }
        }
        return false;
    }

    public String extractGuidelineFromLlmOutput(String raw) {
        if (raw == null || raw.isBlank() || raw.startsWith("[stub-llm]") || raw.startsWith("[9router-error]")) {
            return null;
        }
        Matcher closedMatcher = EVOLVED_TAG_PATTERN.matcher(raw);
        if (closedMatcher.find()) {
            String extracted = sanitizeRuleText(closedMatcher.group(1));
            if (!extracted.isBlank()) {
                return truncate(extracted, 280);
            }
        }
        Matcher unclosedMatcher = UNCLOSED_EVOLVED_TAG_PATTERN.matcher(raw);
        if (unclosedMatcher.find()) {
            String extracted = sanitizeRuleText(unclosedMatcher.group(1));
            if (!extracted.isBlank()) {
                return truncate(extracted, 280);
            }
        }
        return null;
    }

    private String tryGenerateGuidelineViaLlm(InteractionEpisode episode,
                                              EvaluationResult eval,
                                              CustomerFeedbackRequest explicitFeedback) {
        try {
            CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
            String systemPrompt = (evo != null && evo.getOptimizerSystemPrompt() != null
                    && !evo.getOptimizerSystemPrompt().isBlank())
                    ? evo.getOptimizerSystemPrompt().strip()
                    : "Hãy đúc kết 1 quy tắc cải tiến ngắn gọn đặt trong thẻ <EVOLVED_GUIDELINE>...</EVOLVED_GUIDELINE>.";

            StringBuilder prompt = new StringBuilder();
            prompt.append(systemPrompt).append("\n\n");
            prompt.append("- Kịch bản: ").append(episode.scenario()).append("\n");
            prompt.append("- Câu hỏi của khách hàng: ").append(sanitizeRuleText(episode.question())).append("\n");
            prompt.append("- Câu trả lời hiện tại: ").append(truncate(sanitizeRuleText(episode.answer()), 400)).append("\n");
            prompt.append("- Đánh giá Loss: ").append(eval.lossCritique()).append("\n");
            prompt.append("- Textual Gradient: ").append(eval.textualGradient()).append("\n");
            if (explicitFeedback != null && explicitFeedback.feedback() != null && !explicitFeedback.feedback().isBlank()) {
                prompt.append("- Góp ý trực tiếp của khách hàng: ")
                      .append(sanitizeRuleText(explicitFeedback.feedback())).append("\n");
            }
            prompt.append("\nHãy trả về quy tắc cải tiến trong cặp thẻ <EVOLVED_GUIDELINE>...</EVOLVED_GUIDELINE>:");

            String raw = llmClient.complete(prompt.toString());
            return extractGuidelineFromLlmOutput(raw);
        } catch (Exception e) {
            log.debug("LLM TextGrad reflection skipped ({}); using deterministic TextGrad rule synthesis", e.getMessage());
        }
        return null;
    }

    private String synthesizeDeterministicGuideline(CustomerScenario scenario,
                                                    List<CustomerScenario> secondaryScenarios,
                                                    String textualGradient,
                                                    String explicitFeedbackText,
                                                    boolean hasRagChunks) {
        if (explicitFeedbackText != null && !explicitFeedbackText.isBlank()) {
            String cleaned = truncate(sanitizeRuleText(explicitFeedbackText), 200);
            return "Ưu tiên lưu ý từ phản hồi thực tế của khách hàng (" + scenario.code() + "): " + cleaned;
        }
        if (secondaryScenarios != null && !secondaryScenarios.isEmpty()) {
            String secCodes = String.join(", ", secondaryScenarios.stream().map(CustomerScenario::code).toList());
            return "Khi khách hàng hỏi kết hợp " + scenario.code() + " và [" + secCodes
                    + "], cần chia mục rõ ràng để giải đáp trọn vẹn cả nhu cầu chính lẫn chính sách liên quan trong cùng một câu trả lời.";
        }
        if (textualGradient != null && !textualGradient.isBlank()
                && !textualGradient.startsWith("Duy trì phong cách")) {
            return truncate(sanitizeRuleText(textualGradient), 240);
        }
        return scenario.defaultEvolutionGuideline(customerProps, hasRagChunks);
    }

    private void upsertGuideline(String scenarioCode, String rawRule, String sourceGradient, double confidence) {
        String cleanRule = sanitizeRuleText(rawRule);
        if (cleanRule.isBlank()) {
            return;
        }
        String normForHash = CustomerScenario.normalizeWordsPadded(cleanRule).strip();
        String hash = FileValidation.sha256Hex(normForHash.getBytes(StandardCharsets.UTF_8));
        String now = OffsetDateTime.now().toString();

        List<LearnedGuidelineInfo> list = learnedGuidelines.computeIfAbsent(scenarioCode, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            LearnedGuidelineInfo existing = list.get(i);
            if (existing.contentHash().equals(hash)) {
                double boostedConfidence = Math.round(
                        Math.min(0.99, Math.max(existing.confidenceScore(), confidence) + 0.02) * 1000.0
                ) / 1000.0;
                LearnedGuidelineInfo updated = new LearnedGuidelineInfo(
                        existing.id(),
                        scenarioCode,
                        existing.rule(),
                        sourceGradient != null ? sanitizeRuleText(sourceGradient) : existing.sourceGradient(),
                        boostedConfidence,
                        existing.reinforcementCount() + 1,
                        hash,
                        now
                );
                list.set(i, updated);
                return;
            }
        }

        LearnedGuidelineInfo created = new LearnedGuidelineInfo(
                UUID.randomUUID().toString(),
                scenarioCode,
                cleanRule,
                sourceGradient != null ? sanitizeRuleText(sourceGradient) : "",
                Math.round(confidence * 1000.0) / 1000.0,
                1,
                hash,
                now
        );
        list.add(created);

        int maxGuidelines = customerProps.getEvolution() != null
                && customerProps.getEvolution().getMaxLearnedGuidelinesPerScenario() > 0
                ? customerProps.getEvolution().getMaxLearnedGuidelinesPerScenario()
                : 10;
        if (list.size() > maxGuidelines) {
            list.sort(Comparator.comparingDouble(
                    (LearnedGuidelineInfo g) -> g.confidenceScore() + 0.05 * g.reinforcementCount()
            ).reversed());
            while (list.size() > maxGuidelines) {
                list.remove(list.size() - 1);
            }
        }
    }

    private boolean evictExemplarIfPresent(String scenarioCode, String question) {
        if (question == null || question.isBlank()) {
            return false;
        }
        String cleanQ = sanitizeRuleText(question);
        String normKey = CustomerScenario.normalizeWordsPadded(cleanQ).strip();
        if (normKey.isEmpty()) {
            return false;
        }
        String hash = FileValidation.sha256Hex(normKey.getBytes(StandardCharsets.UTF_8));
        List<EvolvedExampleInfo> list = evolvedExamples.get(scenarioCode);
        if (list == null || list.isEmpty()) {
            return false;
        }
        return list.removeIf(ex -> ex != null && hash.equals(ex.contentHash()));
    }

    private void upsertExemplar(String scenarioCode, String question, String answer, double qualityScore) {
        String cleanQ = sanitizeRuleText(question);
        String cleanA = truncate(sanitizeRuleText(answer), 360);
        if (cleanQ.isBlank() || cleanA.isBlank()) {
            return;
        }
        String normKey = CustomerScenario.normalizeWordsPadded(cleanQ).strip();
        String hash = FileValidation.sha256Hex(normKey.getBytes(StandardCharsets.UTF_8));
        String now = OffsetDateTime.now().toString();

        List<EvolvedExampleInfo> list = evolvedExamples.computeIfAbsent(scenarioCode, k -> new ArrayList<>());
        for (int i = 0; i < list.size(); i++) {
            EvolvedExampleInfo existing = list.get(i);
            if (existing.contentHash().equals(hash)) {
                EvolvedExampleInfo updated = new EvolvedExampleInfo(
                        existing.id(),
                        scenarioCode,
                        cleanQ,
                        qualityScore >= existing.qualityScore() ? cleanA : existing.assistantAnswer(),
                        Math.max(existing.qualityScore(), qualityScore),
                        existing.reinforcementCount() + 1,
                        hash,
                        now
                );
                list.set(i, updated);
                return;
            }
        }

        list.add(new EvolvedExampleInfo(
                UUID.randomUUID().toString(),
                scenarioCode,
                cleanQ,
                cleanA,
                qualityScore,
                1,
                hash,
                now
        ));

        int maxExemplars = customerProps.getEvolution() != null
                && customerProps.getEvolution().getMaxFewShotExemplarsPerScenario() > 0
                ? customerProps.getEvolution().getMaxFewShotExemplarsPerScenario()
                : 4;
        if (list.size() > maxExemplars) {
            list.sort(Comparator.comparingDouble(
                    (EvolvedExampleInfo e) -> e.qualityScore() + 0.03 * e.reinforcementCount()
            ).reversed());
            while (list.size() > maxExemplars) {
                list.remove(list.size() - 1);
            }
        }
    }

    private void storeEpisode(InteractionEpisode ep) {
        boolean isNew = !episodeStore.containsKey(ep.interactionId());
        episodeStore.put(ep.interactionId(), ep);
        if (isNew) {
            episodeOrder.addLast(ep.interactionId());
        }
        int maxHistory = customerProps.getEvolution() != null
                && customerProps.getEvolution().getMaxEpisodeHistorySize() > 0
                ? customerProps.getEvolution().getMaxEpisodeHistorySize()
                : 200;
        while (episodeOrder.size() > maxHistory) {
            String oldest = episodeOrder.removeFirst();
            episodeStore.remove(oldest);
        }
    }

    private void recordGradient(TextualGradientInfo gradientInfo) {
        recentGradients.addFirst(gradientInfo);
        int maxGradients = customerProps.getEvolution() != null
                && customerProps.getEvolution().getMaxRecentGradients() > 0
                ? customerProps.getEvolution().getMaxRecentGradients()
                : 50;
        while (recentGradients.size() > maxGradients) {
            recentGradients.removeLast();
        }
    }

    // =========================================================================
    // Lưu trữ bền vững (Long-Term Memory JSON Persistence)
    // =========================================================================

    private void persistToDiskIfEnabled() {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo == null || !evo.isPersistToDisk() || evo.getStoragePath() == null || evo.getStoragePath().isBlank()) {
            return;
        }
        try {
            Path target = Path.of(evo.getStoragePath().strip());
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            CustomerEvolutionStatusResponse snapshot = getStatus();
            objectMapper.writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("Không thể lưu bộ nhớ tự tiến hoá xuống {}: {}", evo.getStoragePath(), e.getMessage());
        }
    }

    private void loadFromDiskIfEnabled() {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo == null || !evo.isPersistToDisk() || evo.getStoragePath() == null || evo.getStoragePath().isBlank()) {
            return;
        }
        synchronized (stateLock) {
            try {
                Path target = Path.of(evo.getStoragePath().strip());
                if (!Files.exists(target)) {
                    return;
                }
                CustomerEvolutionStatusResponse snapshot =
                        objectMapper.readValue(target.toFile(), CustomerEvolutionStatusResponse.class);
                if (snapshot != null) {
                    evolutionGeneration.set(Math.max(0, snapshot.evolutionGeneration()));
                    totalInteractions.set(Math.max(0L, snapshot.totalInteractions()));
                    totalFeedbackCount.set(Math.max(0L, snapshot.totalFeedbackCount()));
                    cumulativeQualityScore = snapshot.averageQualityScore() * snapshot.totalInteractions();

                    if (snapshot.learnedGuidelines() != null) {
                        snapshot.learnedGuidelines().forEach((k, v) -> {
                            if (k != null && v != null) {
                                learnedGuidelines.put(k.toUpperCase(Locale.ROOT), new ArrayList<>(v));
                            }
                        });
                    }
                    if (snapshot.evolvedFewShotExamples() != null) {
                        snapshot.evolvedFewShotExamples().forEach((k, v) -> {
                            if (k != null && v != null) {
                                evolvedExamples.put(k.toUpperCase(Locale.ROOT), new ArrayList<>(v));
                            }
                        });
                    }
                    if (snapshot.recentGradients() != null) {
                        recentGradients.clear();
                        recentGradients.addAll(snapshot.recentGradients());
                    }
                    log.info("Loaded EvoAgentX customer evolution memory from {} (gen={}, interactions={})",
                            target, evolutionGeneration.get(), totalInteractions.get());
                }
            } catch (Exception e) {
                log.warn("Không thể đọc bộ nhớ tự tiến hoá từ {}: {}", evo.getStoragePath(), e.getMessage());
            }
        }
    }

    private static String sanitizeRuleText(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("(?iu)<\\s*/?\\s*(document_context|store_policy_context|conversation_history|evolved_guidelines|evolved_guideline)[^>]*>?", "")
                .replaceAll("(?iu)#+\\s*(vai\\s+trò|quy\\s+tắc|kịch\\s+bản|mẫu\\s+hội\\s+thoại|câu\\s+hỏi|tri\\s+thức)", "")
                .replaceAll("(?iu)câu\\s+hỏi\\s*:", "nội dung hỏi:")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        return t.length() <= maxLen ? t : t.substring(0, maxLen) + "…";
    }

    private enum ImplicitSignal {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }

    private record EvaluationResult(
            double qualityScore,
            String lossCritique,
            String textualGradient
    ) {
    }

    public record InteractionEpisode(
            String interactionId,
            String question,
            String answer,
            String scenario,
            List<String> secondaryScenarios,
            int retrievedChunkCount,
            String modelName,
            double qualityScore,
            String timestamp
    ) {
    }
}

package com.example.docmind.service.evolution;

import com.example.docmind.config.CustomerSupportProperties;
import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.ChunkUtilityInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.EvolvedExampleInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.LearnedGuidelineInfo;
import com.example.docmind.dto.CustomerEvolutionStatusResponse.TextualGradientInfo;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.repository.RetrievedChunk;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Động cơ Tự tiến hoá & Học hỏi sau các lần chat với khách hàng (EvoAgentX Self-Evolving Engine).
 *
 * <p>Thiết kế dựa trên 6 trụ cột tiến hoá của <b>EvoAgentX</b>:
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
 *   <li><b>GEPA (Genetic-Pareto Prompt Evolution)</b>: Lai ghép di truyền (Crossover) các quy tắc mạnh
 *       và xếp hạng không bị trội (Non-dominated Pareto Frontier Sorting) trên 3 trục mục tiêu:
 *       An toàn/Chính sách, Văn phong tự nhiên, và Độ đầy đủ đa ý định.</li>
 *   <li><b>Self-Evolving RAG (Dynamic Query Expansion & Adaptive Chunk Utility Boosting)</b>:
 *       Tự học từ đồng nghĩa/slang từ hội thoại thực tế và cộng điểm ưu tiên RRF cho các Chunk tài liệu
 *       giúp tạo ra câu trả lời chất lượng cao.</li>
 *   <li><b>AFlow Adaptive Workflow Topology & Self-Critic Loop</b>: Tự động định tuyến câu hỏi rủi ro cao
 *       (thai kỳ, kích ứng 14 ngày, đa ý định/báo giá) qua vòng lặp Tự kiểm duyệt 2 bước
 *       ({@code Generate -> Critic -> Revise}).</li>
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

    public static final String TOPOLOGY_FAST_DIRECT = "FAST_DIRECT_RAG";
    public static final String TOPOLOGY_MULTI_INTENT = "MULTI_INTENT_SYNTHESIS_DAG";
    public static final String TOPOLOGY_AFLOW_CRITIC = "AFLOW_SELF_CRITIC_LOOP";

    public static final String TOPOLOGY_FAST_DIRECT_RAG = TOPOLOGY_FAST_DIRECT;
    public static final String TOPOLOGY_MULTI_INTENT_DAG = TOPOLOGY_MULTI_INTENT;
    public static final String TOPOLOGY_AFLOW_SELF_CRITIC = TOPOLOGY_AFLOW_CRITIC;

    private final CustomerSupportProperties customerProps;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final Object stateLock = new Object();

    private final AtomicInteger evolutionGeneration = new AtomicInteger(0);
    private final AtomicLong totalInteractions = new AtomicLong(0);
    private final AtomicLong totalFeedbackCount = new AtomicLong(0);
    private final AtomicInteger gepaCrossoverCount = new AtomicInteger(0);
    private final AtomicLong aflowCriticRevisions = new AtomicLong(0);
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

    /** Từ điển mở rộng truy vấn tự học (Self-Evolving RAG Learned Query Expansions). */
    private final Map<String, String> learnedQueryExpansions = new LinkedHashMap<>();

    /** Điểm uy tín của từng Chunk tài liệu (Self-Evolving RAG Adaptive Chunk Utility Boosting). */
    private final Map<String, ChunkUtilityInfo> chunkUtilityScores = new LinkedHashMap<>();

    /** Thống kê số lượt thực thi theo từng cấu trúc luồng AFlow (Workflow Topology Stats). */
    private final Map<String, Long> workflowTopologyStats = new LinkedHashMap<>();

    /** Đường dẫn tệp bộ nhớ tự tiến hoá đã được định vị thành công. */
    private volatile Path resolvedStoragePath;

    public Path getResolvedStoragePath() {
        return resolvedStoragePath;
    }

    public String getResolvedStateFilePath() {
        return resolvedStoragePath != null ? resolvedStoragePath.toString() : null;
    }

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
        seedDefaultSelfEvolvingRagAndTopologyState();
        loadFromDiskIfEnabled();
    }

    private void seedDefaultSelfEvolvingRagAndTopologyState() {
        synchronized (stateLock) {
            CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
            if (evo == null || evo.isDomainGuardrailsEnabled()) {
                Map<String, String> configuredSeeds = (evo != null && evo.getSeedQueryExpansions() != null)
                        ? evo.getSeedQueryExpansions()
                        : com.example.docmind.config.DomainKnowledgePack.loadDefault().getSeedQueryExpansions();
                if (configuredSeeds != null) {
                    configuredSeeds.forEach((k, v) -> {
                        if (k != null && !k.isBlank() && v != null && !v.isBlank()) {
                            learnedQueryExpansions.putIfAbsent(k.strip(), v.strip());
                        }
                    });
                }
            }

            workflowTopologyStats.putIfAbsent(TOPOLOGY_FAST_DIRECT, 0L);
            workflowTopologyStats.putIfAbsent(TOPOLOGY_MULTI_INTENT, 0L);
            workflowTopologyStats.putIfAbsent(TOPOLOGY_AFLOW_CRITIC, 0L);
        }
    }

    /**
     * Lấy danh sách các quy tắc đã tiến hoá (Learned Guidelines) áp dụng cho kịch bản hiện tại,
     * ưu tiên các quy tắc nằm trên biên Pareto tối ưu (GEPA Pareto Rank 1).
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
                    .sorted(Comparator.comparingDouble(CustomerSelfEvolutionService::gepaRankingScore).reversed())
                    .limit(maxInPrompt)
                    .toList();
        }
    }

    private static double gepaRankingScore(LearnedGuidelineInfo g) {
        if (g == null) {
            return 0.0;
        }
        double paretoBonus = g.paretoOptimal() ? 0.15 : Math.max(0.0, 0.08 - 0.03 * Math.max(0, g.paretoRank() - 1));
        double crossoverBonus = "GEPA_CROSSOVER".equalsIgnoreCase(g.generationMethod()) ? 0.04 : 0.0;
        double multiObjectiveMean = (g.safetyPolicyScore() + g.naturalnessToneScore() + g.completenessActionScore()) / 3.0;
        return 0.55 * g.confidenceScore()
                + 0.45 * multiObjectiveMean
                + 0.05 * g.reinforcementCount()
                + paretoBonus
                + crossoverBonus;
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

    public List<EvolvedExampleInfo> getActiveExemplarsForScenario(CustomerScenario scenario) {
        return getActiveExamplesForScenario(scenario);
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

        // Đánh giá chất lượng câu trả lời theo đa tiêu chí + 3 trục GEPA Pareto (EvoAgentX Loss Evaluation)
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
                // Self-Evolving RAG: Cập nhật điểm uy tín Chunk & tự học từ khoá mở rộng truy vấn
                updateChunkUtilityScores(fusedChunks, eval.qualityScore(), implicitSignal, null);
                learnQueryExpansionFromInteraction(question, fusedChunks, eval.qualityScore());
                persistToDiskIfEnabled();
            }
        }

        return interactionId;
    }

    /**
     * Tiếp nhận phản hồi tường minh của khách hàng (POST /api/customer-chat/feedback)
     * và chạy ngay bước cập nhật đạo hàm văn bản (TextGrad step) + GEPA Pareto + cập nhật bộ nhớ dài hạn.
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
     * Kích hoạt chủ động 1 chu kỳ tối ưu hoá EvoAgentX TextGrad + MIPRO + GEPA Genetic-Pareto Crossover.
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
                CustomerScenario sc = targetScenario != null ? targetScenario : CustomerScenario.SALES;
                boolean crossed = performGepaGeneticCrossover(sc);
                if (!crossed) {
                    String seedRule = synthesizeDeterministicGuideline(
                            sc,
                            List.of(),
                            "Khách hàng cần thông tin tư vấn rõ ràng, báo giá minh bạch và hướng dẫn từng bước.",
                            null,
                            true
                    );
                    upsertGuideline(sc.code(), seedRule, "EvoAgentX proactive baseline initialization",
                            0.85, "TEXTGRAD_MUTATION", 0.90, 0.90, 0.85);
                }
                int gen = evolutionGeneration.incrementAndGet();
                recordGradient(new TextualGradientInfo(
                        UUID.randomUUID().toString(),
                        sc.code(),
                        "(Proactive Evolution Cycle — GEPA + TextGrad)",
                        0.92,
                        "Khởi tạo chu kỳ tiến hoá chủ động và tối ưu biên Pareto GEPA cho kịch bản " + sc.code() + ".",
                        "Lai ghép quy tắc GEPA Crossover và củng cố biên Pareto cho kịch bản " + sc.code(),
                        (crossed ? "GEPA_PARETO_CROSSOVER" : "EVOLVED_GUIDELINE_UPSERT") + " (gen=" + gen + ")",
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
            // Thực thi thêm bước lai ghép di truyền GEPA (Genetic-Pareto Crossover)
            CustomerScenario crossoverTarget = targetScenario != null ? targetScenario : CustomerScenario.SALES;
            if (performGepaGeneticCrossover(crossoverTarget)) {
                int gen = evolutionGeneration.incrementAndGet();
                recordGradient(new TextualGradientInfo(
                        UUID.randomUUID().toString(),
                        crossoverTarget.code(),
                        "(GEPA Genetic-Pareto Crossover)",
                        0.96,
                        "Lai ghép 2 quy tắc dẫn đầu biên Pareto (An toàn chính sách x Đa ý định/Văn phong).",
                        "Tổng hợp quy tắc con GEPA-Crossover tối ưu đồng thời 3 mục tiêu Pareto cho " + crossoverTarget.code(),
                        "GEPA_PARETO_CROSSOVER (gen=" + gen + ")",
                        OffsetDateTime.now().toString()
                ));
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
            gepaCrossoverCount.set(0);
            aflowCriticRevisions.set(0);
            cumulativeQualityScore = 0.0;
            for (CustomerScenario sc : CustomerScenario.values()) {
                learnedGuidelines.put(sc.code(), new ArrayList<>());
                evolvedExamples.put(sc.code(), new ArrayList<>());
            }
            episodeStore.clear();
            episodeOrder.clear();
            recentGradients.clear();
            learnedQueryExpansions.clear();
            chunkUtilityScores.clear();
            workflowTopologyStats.clear();
            seedDefaultSelfEvolvingRagAndTopologyState();
            persistToDiskIfEnabled();
            return getStatus();
        }
    }

    /**
     * Trả về trạng thái hiện tại của động cơ tự tiến hoá EvoAgentX (bao gồm GEPA, Self-Evolving RAG, AFlow).
     */
    public CustomerEvolutionStatusResponse getStatus() {
        synchronized (stateLock) {
            CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
            long interactions = totalInteractions.get();
            double avgScore = interactions > 0
                    ? Math.round(Math.max(0.0, Math.min(1.0, cumulativeQualityScore / interactions)) * 1000.0) / 1000.0
                    : 0.0;

            Map<String, List<LearnedGuidelineInfo>> guidelinesCopy = new LinkedHashMap<>();
            int paretoFrontCount = 0;
            for (CustomerScenario sc : CustomerScenario.values()) {
                recomputeParetoFrontier(sc.code());
                List<LearnedGuidelineInfo> list = List.copyOf(learnedGuidelines.getOrDefault(sc.code(), List.of()));
                guidelinesCopy.put(sc.code(), list);
                for (LearnedGuidelineInfo g : list) {
                    if (g != null && g.paretoOptimal()) {
                        paretoFrontCount++;
                    }
                }
            }

            Map<String, List<EvolvedExampleInfo>> examplesCopy = new LinkedHashMap<>();
            for (CustomerScenario sc : CustomerScenario.values()) {
                examplesCopy.put(sc.code(), List.copyOf(evolvedExamples.getOrDefault(sc.code(), List.of())));
            }

            List<ChunkUtilityInfo> sortedChunks = chunkUtilityScores.values().stream()
                    .sorted(Comparator.comparingDouble(ChunkUtilityInfo::utilityBoost).reversed())
                    .limit(20)
                    .toList();

            return new CustomerEvolutionStatusResponse(
                    evo == null || evo.isEnabled(),
                    evo != null ? evo.getStrategy() : "evoagentx-textgrad-mipro-gepa-aflow",
                    evolutionGeneration.get(),
                    interactions,
                    totalFeedbackCount.get(),
                    avgScore,
                    guidelinesCopy,
                    examplesCopy,
                    List.copyOf(recentGradients),
                    paretoFrontCount,
                    gepaCrossoverCount.get(),
                    learnedQueryExpansions.size(),
                    chunkUtilityScores.size(),
                    aflowCriticRevisions.get(),
                    new LinkedHashMap<>(learnedQueryExpansions),
                    sortedChunks,
                    new LinkedHashMap<>(workflowTopologyStats)
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
            String method = (candidateGuideline != null && !candidateGuideline.isBlank())
                    ? "LLM_TEXTGRAD_REFLECTION"
                    : "TEXTGRAD_MUTATION";
            if ((candidateGuideline == null || candidateGuideline.isBlank())
                    && invokeLlmOptimizer && llmClient != null) {
                candidateGuideline = tryGenerateGuidelineViaLlm(episode, eval, explicitFeedback);
                if (candidateGuideline != null && !candidateGuideline.isBlank()) {
                    method = "LLM_TEXTGRAD_REFLECTION";
                }
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
            upsertGuideline(
                    scenario.code(),
                    candidateGuideline,
                    eval.textualGradient(),
                    guidelineConfidence,
                    method,
                    eval.safetyPolicyScore(),
                    eval.naturalnessToneScore(),
                    eval.completenessActionScore()
            );
            int gen = evolutionGeneration.incrementAndGet();
            appliedAction = "EVOLVED_GUIDELINE_UPSERT (gen=" + gen + ")";

            // Nếu là câu hỏi đa ý định hoặc đã tích luỹ đủ >= 2 quy tắc trong kịch bản, kích hoạt lai ghép GEPA
            if (!secondaries.isEmpty() && learnedGuidelines.getOrDefault(scenario.code(), List.of()).size() >= 2) {
                if (performGepaGeneticCrossover(scenario)) {
                    int crossGen = evolutionGeneration.incrementAndGet();
                    appliedAction = appliedAction + " + GEPA_CROSSOVER (gen=" + crossGen + ")";
                }
            }
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

        // 2. Độ chính xác nội dung & Diễn đạt tự nhiên (Natural Grounding — không dùng ký hiệu trích dẫn máy móc)
        double groundingScore = 0.85;
        double safetyPolicyScore = 0.96;
        if (retrievedChunkCount > 0) {
            Matcher m = CITATION_MARKER_PATTERN.matcher(safeAnswer);
            if (m.find()) {
                groundingScore = 0.70;
                critiques.add("Câu trả lời cho khách mua hàng vẫn còn chứa ký hiệu trích dẫn tài liệu dạng [n].");
                gradients.add("Khi tư vấn cho người mua hàng, tuyệt đối không gắn ký hiệu trích dẫn [1], [2] hay nhắc tới tài liệu nguồn; hãy lồng ghép thông tin tự nhiên như kiến thức của chuyên viên.");
            } else if (safeAnswer.length() >= 40) {
                groundingScore = 1.0;
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
                    safetyPolicyScore = Math.max(0.65, safetyPolicyScore - 0.15);
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

        // 3b. Kiểm tra an toàn đối tượng nhạy cảm & rào chắn chính sách (Data-Driven Policy Guardrails)
        if (evo == null || evo.isPregnancySafetyCheckEnabled()) {
            String normQuestion = CustomerScenario.normalizeWordsPadded(question);
            List<String> pregTriggers = (evo != null && evo.getPregnancyTriggerKeywords() != null
                    && !evo.getPregnancyTriggerKeywords().isEmpty())
                    ? evo.getPregnancyTriggerKeywords()
                    : List.of("mang thai", "ba bau", "me bau", "thai ky", "cho con bu");
            List<String> pregSafetyKeywords = (evo != null && evo.getPregnancySafetyRequiredKeywords() != null
                    && !evo.getPregnancySafetyRequiredKeywords().isEmpty())
                    ? evo.getPregnancySafetyRequiredKeywords()
                    : List.of("chong chi dinh", "khong su dung", "khong dung", "tranh", "an toan", "lanh tinh");

            if (containsAnyNormalizedKeyword(normQuestion, pregTriggers)) {
                if (!containsAnyNormalizedKeyword(normAnswer, pregSafetyKeywords)) {
                    toneScore = Math.min(toneScore, 0.45);
                    groundingScore = Math.min(groundingScore, 0.35);
                    completenessScore = Math.max(0.20, completenessScore - 0.50);
                    safetyPolicyScore = 0.25;
                    com.example.docmind.config.DomainKnowledgePack.PolicyGuardrailRule safetyRule =
                            findConfiguredGuardrail("pregnancy-contraindication-safety");
                    critiques.add(safetyRule != null && safetyRule.getCritiqueMessage() != null && !safetyRule.getCritiqueMessage().isBlank()
                            ? safetyRule.getCritiqueMessage()
                            : "Khách hàng thuộc nhóm đối tượng nhạy cảm nhưng câu trả lời chưa nêu rõ cảnh báo chống chỉ định và xác nhận độ an toàn theo tài liệu.");
                    gradients.add(safetyRule != null && safetyRule.getGradientInstruction() != null && !safetyRule.getGradientInstruction().isBlank()
                            ? safetyRule.getGradientInstruction()
                            : "Khi tư vấn cho đối tượng nhạy cảm (mang thai, trẻ nhỏ, bệnh lý nền), bắt buộc cảnh báo rõ các thành phần/dịch vụ chống chỉ định và chỉ gợi ý phương án an toàn trong tài liệu.");
                } else {
                    safetyPolicyScore = 0.99;
                }
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

        score = round3(Math.max(0.0, Math.min(1.0, score)));
        double naturalnessAxis = round3(0.5 * toneScore + 0.5 * groundingScore);
        double completenessAxis = round3(0.6 * completenessScore + 0.4 * actionScore);
        double safetyAxis = round3(Math.max(0.2, Math.min(0.99, 0.7 * safetyPolicyScore + 0.3 * groundingScore)));

        String lossCritique = critiques.isEmpty()
                ? "Câu trả lời đạt chuẩn tư vấn khách hàng trên cả 4 tiêu chí (Văn phong tự nhiên, Độ chính xác, Độ đầy đủ, Gợi mở hỗ trợ)."
                : String.join(" ", critiques);
        String textualGradient = gradients.isEmpty()
                ? "Duy trì văn phong tư vấn tự nhiên như người thật, thân thiện, chuẩn xác thông tin (không trích dẫn nguồn) và gợi mở bước tiếp theo cho kịch bản " + scenario.code() + "."
                : String.join(" ", gradients);

        return new EvaluationResult(score, lossCritique, textualGradient, safetyAxis, naturalnessAxis, completenessAxis);
    }

    private static double round3(double val) {
        return Math.round(val * 1000.0) / 1000.0;
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
                    + "], hãy tư vấn liền mạch, tự nhiên như người thật để giải đáp trọn vẹn cả nhu cầu chính lẫn chính sách liên quan trong cùng một câu trả lời.";
        }
        if (textualGradient != null && !textualGradient.isBlank()
                && !textualGradient.startsWith("Duy trì phong cách")
                && !textualGradient.startsWith("Duy trì văn phong")) {
            return truncate(sanitizeRuleText(textualGradient), 240);
        }
        return scenario.defaultEvolutionGuideline(customerProps, hasRagChunks);
    }

    private void upsertGuideline(String scenarioCode, String rawRule, String sourceGradient, double confidence) {
        upsertGuideline(scenarioCode, rawRule, sourceGradient, confidence,
                "TEXTGRAD_MUTATION", confidence, confidence, confidence);
    }

    private void upsertGuideline(String scenarioCode,
                                 String rawRule,
                                 String sourceGradient,
                                 double confidence,
                                 String generationMethod,
                                 double safetyScore,
                                 double naturalnessScore,
                                 double completenessScore) {
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
                double boostedConfidence = round3(Math.min(0.99, Math.max(existing.confidenceScore(), confidence) + 0.02));
                LearnedGuidelineInfo updated = new LearnedGuidelineInfo(
                        existing.id(),
                        scenarioCode,
                        existing.rule(),
                        sourceGradient != null ? sanitizeRuleText(sourceGradient) : existing.sourceGradient(),
                        boostedConfidence,
                        existing.reinforcementCount() + 1,
                        hash,
                        now,
                        existing.generationMethod() != null ? existing.generationMethod() : generationMethod,
                        existing.paretoOptimal(),
                        existing.paretoRank(),
                        round3(Math.max(existing.safetyPolicyScore(), safetyScore)),
                        round3(Math.max(existing.naturalnessToneScore(), naturalnessScore)),
                        round3(Math.max(existing.completenessActionScore(), completenessScore))
                );
                list.set(i, updated);
                recomputeParetoFrontier(scenarioCode);
                return;
            }
        }

        LearnedGuidelineInfo created = new LearnedGuidelineInfo(
                UUID.randomUUID().toString(),
                scenarioCode,
                cleanRule,
                sourceGradient != null ? sanitizeRuleText(sourceGradient) : "",
                round3(confidence),
                1,
                hash,
                now,
                generationMethod != null ? generationMethod : "TEXTGRAD_MUTATION",
                true,
                1,
                round3(safetyScore),
                round3(naturalnessScore),
                round3(completenessScore)
        );
        list.add(created);
        recomputeParetoFrontier(scenarioCode);

        int maxGuidelines = customerProps.getEvolution() != null
                && customerProps.getEvolution().getMaxLearnedGuidelinesPerScenario() > 0
                ? customerProps.getEvolution().getMaxLearnedGuidelinesPerScenario()
                : 10;
        if (list.size() > maxGuidelines) {
            list.sort(Comparator.comparingDouble(CustomerSelfEvolutionService::gepaRankingScore).reversed());
            while (list.size() > maxGuidelines) {
                list.remove(list.size() - 1);
            }
        }
    }

    // =========================================================================
    // Trụ cột 4: GEPA (Genetic-Pareto Prompt Evolution — Non-Dominated Sorting & Crossover)
    // =========================================================================

    /**
     * Tính toán thứ hạng không bị trội (Non-Dominated Pareto Rank — NSGA-II / GEPA)
     * cho toàn bộ quy tắc trong kịch bản trên 3 trục:
     * (1) safetyPolicyScore, (2) naturalnessToneScore, (3) completenessActionScore.
     */
    private void recomputeParetoFrontier(String scenarioCode) {
        List<LearnedGuidelineInfo> list = learnedGuidelines.get(scenarioCode);
        if (list == null || list.isEmpty()) {
            return;
        }
        int n = list.size();
        int[] ranks = new int[n];
        for (int i = 0; i < n; i++) {
            LearnedGuidelineInfo a = list.get(i);
            int dominatedByCount = 0;
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                LearnedGuidelineInfo b = list.get(j);
                if (paretoDominates(b, a)) {
                    dominatedByCount++;
                }
            }
            ranks[i] = 1 + dominatedByCount;
        }
        for (int i = 0; i < n; i++) {
            LearnedGuidelineInfo g = list.get(i);
            int rank = ranks[i];
            boolean optimal = (rank == 1);
            if (g.paretoRank() != rank || g.paretoOptimal() != optimal) {
                list.set(i, new LearnedGuidelineInfo(
                        g.id(),
                        g.scenario(),
                        g.rule(),
                        g.sourceGradient(),
                        g.confidenceScore(),
                        g.reinforcementCount(),
                        g.contentHash(),
                        g.updatedAt(),
                        g.generationMethod() != null ? g.generationMethod() : "TEXTGRAD_MUTATION",
                        optimal,
                        rank,
                        g.safetyPolicyScore() > 0 ? g.safetyPolicyScore() : g.confidenceScore(),
                        g.naturalnessToneScore() > 0 ? g.naturalnessToneScore() : g.confidenceScore(),
                        g.completenessActionScore() > 0 ? g.completenessActionScore() : g.confidenceScore()
                ));
            }
        }
    }

    private static boolean paretoDominates(LearnedGuidelineInfo b, LearnedGuidelineInfo a) {
        if (a == null || b == null) {
            return false;
        }
        double bSafe = b.safetyPolicyScore() > 0 ? b.safetyPolicyScore() : b.confidenceScore();
        double bNat = b.naturalnessToneScore() > 0 ? b.naturalnessToneScore() : b.confidenceScore();
        double bComp = b.completenessActionScore() > 0 ? b.completenessActionScore() : b.confidenceScore();

        double aSafe = a.safetyPolicyScore() > 0 ? a.safetyPolicyScore() : a.confidenceScore();
        double aNat = a.naturalnessToneScore() > 0 ? a.naturalnessToneScore() : a.confidenceScore();
        double aComp = a.completenessActionScore() > 0 ? a.completenessActionScore() : a.confidenceScore();

        boolean geAll = bSafe >= aSafe && bNat >= aNat && bComp >= aComp;
        boolean gtAny = bSafe > aSafe || bNat > aNat || bComp > aComp;
        return geAll && gtAny;
    }

    /**
     * Lai ghép di truyền (GEPA Genetic Crossover) giữa 2 quy tắc bổ trợ nhau trên biên Pareto
     * của kịch bản để sinh ra quy tắc thế hệ con kết hợp cả tính an toàn/chính sách lẫn độ đầy đủ đa ý định.
     */
    public boolean performGepaGeneticCrossover(CustomerScenario scenario) {
        CustomerScenario sc = scenario != null ? scenario : CustomerScenario.SALES;
        synchronized (stateLock) {
            recomputeParetoFrontier(sc.code());
            List<LearnedGuidelineInfo> candidates = new ArrayList<>(learnedGuidelines.getOrDefault(sc.code(), List.of()));
            if (candidates.size() < 2) {
                List<LearnedGuidelineInfo> genList = learnedGuidelines.getOrDefault(CustomerScenario.GENERAL.code(), List.of());
                for (LearnedGuidelineInfo g : genList) {
                    if (candidates.stream().noneMatch(c -> c.contentHash().equals(g.contentHash()))) {
                        candidates.add(g);
                    }
                }
            }
            if (candidates.size() < 2) {
                return false;
            }

            // Chọn Cha/Mẹ 1 mạnh nhất về An toàn/Chính sách và Cha/Mẹ 2 mạnh nhất về Độ đầy đủ/Văn phong
            LearnedGuidelineInfo parentA = candidates.stream()
                    .max(Comparator.comparingDouble(LearnedGuidelineInfo::safetyPolicyScore)
                            .thenComparingDouble(LearnedGuidelineInfo::confidenceScore))
                    .orElse(candidates.get(0));

            LearnedGuidelineInfo parentB = candidates.stream()
                    .filter(g -> !g.contentHash().equals(parentA.contentHash()))
                    .max(Comparator.comparingDouble((LearnedGuidelineInfo g) ->
                            g.completenessActionScore() + g.naturalnessToneScore()))
                    .orElse(candidates.get(1));

            String partA = extractCoreClause(parentA.rule(), 125);
            String partB = extractCoreClause(parentB.rule(), 125);
            String offspringRule = "[GEPA-Crossover] Kết hợp chuẩn Pareto (" + sc.code() + "): "
                    + partA + "; đồng thời " + decapitalizeFirst(partB);
            offspringRule = truncate(sanitizeRuleText(offspringRule), 260);

            String normHash = FileValidation.sha256Hex(
                    CustomerScenario.normalizeWordsPadded(offspringRule).strip().getBytes(StandardCharsets.UTF_8));
            boolean alreadyExists = learnedGuidelines.getOrDefault(sc.code(), List.of()).stream()
                    .anyMatch(g -> g.contentHash().equals(normHash));

            double childSafe = round3(Math.min(0.99, Math.max(parentA.safetyPolicyScore(), parentB.safetyPolicyScore()) + 0.01));
            double childNat = round3(Math.min(0.99, Math.max(parentA.naturalnessToneScore(), parentB.naturalnessToneScore()) + 0.01));
            double childComp = round3(Math.min(0.99, Math.max(parentA.completenessActionScore(), parentB.completenessActionScore()) + 0.01));
            double childConf = round3((childSafe + childNat + childComp) / 3.0);

            upsertGuideline(
                    sc.code(),
                    offspringRule,
                    "GEPA Genetic-Pareto Crossover từ #" + parentA.contentHash().substring(0, 6)
                            + " x #" + parentB.contentHash().substring(0, 6),
                    childConf,
                    "GEPA_CROSSOVER",
                    childSafe,
                    childNat,
                    childComp
            );
            if (!alreadyExists) {
                gepaCrossoverCount.incrementAndGet();
            }
            return true;
        }
    }

    private static String extractCoreClause(String rule, int maxChars) {
        if (rule == null || rule.isBlank()) {
            return "tư vấn chính xác và tận tâm";
        }
        String cleaned = rule.replaceFirst("(?iu)^\\[gepa-crossover]\\s*kết\\s+hợp\\s+chuẩn\\s+pareto\\s*\\([^)]*\\)\\s*:\\s*", "").strip();
        if (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).strip();
        }
        return truncate(cleaned, maxChars);
    }

    private static String decapitalizeFirst(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    // =========================================================================
    // Trụ cột 5: Self-Evolving RAG (Learned Query Expansion & Chunk Utility Boosting)
    // =========================================================================

    /**
     * Mở rộng câu truy vấn RAG bằng từ điển từ đồng nghĩa/slang đã tự học trong quá trình vận hành.
     */
    public String expandQueryWithLearnedSynonyms(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return "";
        }
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo != null && !evo.isEnabled()) {
            return rawQuery;
        }
        String padded = CustomerScenario.normalizeWordsPadded(rawQuery);
        List<String> matchedExpansions = new ArrayList<>();
        synchronized (stateLock) {
            for (Map.Entry<String, String> entry : learnedQueryExpansions.entrySet()) {
                String trigger = CustomerScenario.normalizeWordsPadded(entry.getKey()).strip();
                if (!trigger.isEmpty() && padded.contains(" " + trigger + " ")) {
                    matchedExpansions.add(entry.getValue());
                    if (matchedExpansions.size() >= 5) {
                        break;
                    }
                }
            }
        }
        if (matchedExpansions.isEmpty()) {
            return rawQuery;
        }
        return rawQuery + " " + String.join(" ", matchedExpansions);
    }

    /**
     * Tái xếp hạng (Re-rank) danh sách các FusedChunk dựa trên điểm uy tín (Chunk Utility Boost)
     * mà EvoAgentX đã học được từ các lượt tư vấn thành công trước đó.
     */
    public List<ReciprocalRankFusion.FusedChunk> rerankChunksByLearnedUtility(
            List<ReciprocalRankFusion.FusedChunk> fusedChunks) {
        if (fusedChunks == null || fusedChunks.size() <= 1) {
            return fusedChunks != null ? fusedChunks : List.of();
        }
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo != null && !evo.isEnabled()) {
            return fusedChunks;
        }
        synchronized (stateLock) {
            if (chunkUtilityScores.isEmpty()) {
                return fusedChunks;
            }
            List<ReciprocalRankFusion.FusedChunk> adjusted = new ArrayList<>(fusedChunks.size());
            boolean anyBoostApplied = false;
            for (ReciprocalRankFusion.FusedChunk fc : fusedChunks) {
                if (fc == null || fc.chunk() == null || fc.chunk().chunkId() == null) {
                    continue;
                }
                String key = fc.chunk().chunkId().toString();
                ChunkUtilityInfo util = chunkUtilityScores.get(key);
                if (util != null && Math.abs(util.utilityBoost()) > 1e-6) {
                    double boostedScore = fc.score() * (1.0 + util.utilityBoost()) + util.utilityBoost() * 0.005;
                    adjusted.add(new ReciprocalRankFusion.FusedChunk(fc.chunk(), boostedScore));
                    anyBoostApplied = true;
                } else {
                    adjusted.add(fc);
                }
            }
            if (!anyBoostApplied) {
                return fusedChunks;
            }
            adjusted.sort(Comparator.comparingDouble(ReciprocalRankFusion.FusedChunk::score).reversed());
            return List.copyOf(adjusted);
        }
    }

    private void updateChunkUtilityScores(List<ReciprocalRankFusion.FusedChunk> fusedChunks,
                                          double qualityScore,
                                          ImplicitSignal implicitSignal,
                                          CustomerFeedbackRequest explicitFeedback) {
        if (fusedChunks == null || fusedChunks.isEmpty()) {
            return;
        }
        boolean isPositive = qualityScore >= 0.85
                || implicitSignal == ImplicitSignal.POSITIVE
                || (explicitFeedback != null && (Boolean.TRUE.equals(explicitFeedback.helpful())
                || (explicitFeedback.rating() != null && explicitFeedback.rating() >= 4)));
        boolean isNegative = implicitSignal == ImplicitSignal.NEGATIVE
                || isNegativeExplicitFeedback(explicitFeedback)
                || qualityScore < 0.65;

        if (!isPositive && !isNegative) {
            return;
        }
        String now = OffsetDateTime.now().toString();
        int limit = Math.min(3, fusedChunks.size());
        for (int i = 0; i < limit; i++) {
            ReciprocalRankFusion.FusedChunk fc = fusedChunks.get(i);
            if (fc == null || fc.chunk() == null || fc.chunk().chunkId() == null) {
                continue;
            }
            RetrievedChunk c = fc.chunk();
            String chunkKey = c.chunkId().toString();
            String docKey = c.documentId() != null ? c.documentId().toString() : "local-kb";
            String headingSummary = (c.headingPath() != null && !c.headingPath().isEmpty())
                    ? String.join(" > ", c.headingPath())
                    : truncate(c.text(), 70);

            ChunkUtilityInfo existing = chunkUtilityScores.get(chunkKey);
            int helpful = existing != null ? existing.helpfulCount() : 0;
            int unhelpful = existing != null ? existing.unhelpfulCount() : 0;
            double currentBoost = existing != null ? existing.utilityBoost() : 0.0;

            if (isPositive) {
                helpful++;
                double step = (i == 0) ? 0.025 : 0.015;
                currentBoost = round3(Math.min(0.25, currentBoost + step));
            } else {
                unhelpful++;
                currentBoost = round3(Math.max(-0.15, currentBoost - 0.03));
            }

            chunkUtilityScores.put(chunkKey, new ChunkUtilityInfo(
                    chunkKey,
                    docKey,
                    headingSummary,
                    helpful,
                    unhelpful,
                    currentBoost,
                    now
            ));
        }
    }

    private void learnQueryExpansionFromInteraction(String question,
                                                    List<ReciprocalRankFusion.FusedChunk> fusedChunks,
                                                    double qualityScore) {
        if (fusedChunks == null || fusedChunks.isEmpty() || qualityScore < 0.85) {
            return;
        }
        RetrievedChunk topChunk = fusedChunks.get(0).chunk();
        if (topChunk == null) {
            return;
        }
        String headingKeywords = (topChunk.headingPath() != null && !topChunk.headingPath().isEmpty())
                ? CustomerScenario.normalizeWordsPadded(String.join(" ", topChunk.headingPath())).strip()
                : "";
        String chunkKeyTerms = extractKeyTermsFromChunkText(topChunk.text(), 10);
        String expansionPayload = truncate((headingKeywords + " " + chunkKeyTerms).strip(), 120);
        if (expansionPayload.isBlank()) {
            return;
        }

        // 1. Học từ câu hỏi ngắn của khách hàng -> tiêu đề & thuật ngữ trọng tâm của chunk
        if (question != null && !question.isBlank()) {
            String normQ = CustomerScenario.normalizeWordsPadded(question).strip();
            String[] qTokens = normQ.isEmpty() ? new String[0] : normQ.split("\\s+");
            if (qTokens.length >= 1 && qTokens.length <= 6
                    && !learnedQueryExpansions.containsKey(normQ)
                    && learnedQueryExpansions.size() < 60
                    && !expansionPayload.equals(normQ)) {
                learnedQueryExpansions.put(normQ, expansionPayload);
            }
        }

        // 2. Tự động trích xuất tiêu đề nhánh cuối (leaf heading) làm khóa mở rộng truy vấn đa lĩnh vực
        if (topChunk.headingPath() != null && !topChunk.headingPath().isEmpty() && learnedQueryExpansions.size() < 60) {
            String leafHeading = topChunk.headingPath().get(topChunk.headingPath().size() - 1);
            String normLeaf = CustomerScenario.normalizeWordsPadded(leafHeading).strip();
            String[] leafTokens = normLeaf.isEmpty() ? new String[0] : normLeaf.split("\\s+");
            if (leafTokens.length >= 1 && leafTokens.length <= 4 && !learnedQueryExpansions.containsKey(normLeaf)) {
                learnedQueryExpansions.put(normLeaf, expansionPayload);
            }
        }
    }

    private static String extractKeyTermsFromChunkText(String text, int maxTerms) {
        if (text == null || text.isBlank() || maxTerms <= 0) {
            return "";
        }
        String norm = CustomerScenario.normalizeWordsPadded(text).strip();
        if (norm.isEmpty()) {
            return "";
        }
        Set<String> stop = Set.of("la", "cua", "va", "co", "cho", "thi", "ma", "nhu", "voi", "cac", "nhung",
                "duoc", "khong", "trong", "nay", "do", "khi", "hay", "hoac", "neu", "de", "tu", "den");
        List<String> terms = new ArrayList<>(maxTerms);
        for (String tok : norm.split("\\s+")) {
            if (tok.length() >= 3 && !stop.contains(tok) && !terms.contains(tok)) {
                terms.add(tok);
                if (terms.size() >= maxTerms) {
                    break;
                }
            }
        }
        return String.join(" ", terms);
    }

    // =========================================================================
    // Trụ cột 6: AFlow Adaptive Workflow Topology & Self-Critic Loop (Đa lĩnh vực)
    // =========================================================================

    /**
     * Định tuyến động cấu trúc luồng thực thi AFlow (Workflow Topology) dựa trên độ phức tạp,
     * tín hiệu cảm xúc khách hàng và mức độ rủi ro chính sách/an toàn (đa lĩnh vực).
     */
    public String selectWorkflowTopology(CustomerChatRequest request,
                                         CustomerScenario primaryScenario,
                                         List<CustomerScenario> secondaryScenarios) {
        String question = request != null ? request.resolvedQuestion() : "";
        String normQ = CustomerScenario.normalizeWordsPadded(question);
        ImplicitSignal signal = detectImplicitSignal(request);

        boolean highRiskSafetyOrPolicy = normQ.contains(" mang thai ") || normQ.contains(" ba bau ")
                || normQ.contains(" me bau ") || normQ.contains(" thai ky ") || normQ.contains(" cho con bu ")
                || normQ.contains(" di ung ") || normQ.contains(" kich ung ") || normQ.contains(" man do ")
                || normQ.contains(" ngung su dung ") || normQ.contains(" chong chi dinh ")
                || normQ.contains(" tac dung phu ") || normQ.contains(" bien chung ")
                || normQ.contains(" 99 000 ") || normQ.contains(" 99k ") || normQ.contains(" 0 dong ")
                || normQ.contains(" ignore all ") || normQ.contains(" system prompt ")
                || normQ.contains(" bo qua moi ") || normQ.contains(" jailbreak ")
                || normQ.contains(" mat du lieu ") || normQ.contains(" downtime ")
                || matchesAnyHighRiskGuardrail(normQ)
                || signal == ImplicitSignal.NEGATIVE;

        String selected;
        if (highRiskSafetyOrPolicy) {
            selected = TOPOLOGY_AFLOW_CRITIC;
        } else if (secondaryScenarios != null && !secondaryScenarios.isEmpty()) {
            selected = TOPOLOGY_MULTI_INTENT;
        } else {
            selected = TOPOLOGY_FAST_DIRECT;
        }

        synchronized (stateLock) {
            workflowTopologyStats.merge(selected, 1L, Long::sum);
        }
        return selected;
    }

    /**
     * Thực thi vòng lặp Tự kiểm duyệt & Sửa lỗi 2 bước của AFlow ({@code Draft -> Critic -> Revise})
     * dựa trên ngữ cảnh tài liệu RAG truy xuất thực tế ({@code fusedChunks}) kết hợp cấu hình rào chắn chính sách.
     */
    public String applyAflowSelfCriticAndRevise(String topology,
                                                CustomerChatRequest request,
                                                CustomerScenario primaryScenario,
                                                List<CustomerScenario> secondaryScenarios,
                                                String draftAnswer) {
        return applyAflowSelfCriticAndRevise(
                topology, request, primaryScenario, secondaryScenarios, List.of(), draftAnswer);
    }

    public String applyAflowSelfCriticAndRevise(String topology,
                                                CustomerChatRequest request,
                                                CustomerScenario primaryScenario,
                                                List<CustomerScenario> secondaryScenarios,
                                                List<ReciprocalRankFusion.FusedChunk> fusedChunks,
                                                String draftAnswer) {
        if (draftAnswer == null || draftAnswer.isBlank()) {
            return draftAnswer;
        }
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo != null && !evo.isEnabled()) {
            return draftAnswer;
        }
        if (!TOPOLOGY_AFLOW_CRITIC.equals(topology) && !TOPOLOGY_MULTI_INTENT.equals(topology)) {
            return draftAnswer;
        }

        String question = request != null ? request.resolvedQuestion() : "";
        String normQ = CustomerScenario.normalizeWordsPadded(question);
        String normA = CustomerScenario.normalizeWordsPadded(draftAnswer);
        List<String> revisions = new ArrayList<>();

        // 1. Kiểm duyệt động theo danh sách PolicyGuardrailRule ngoại hoá (nếu được bật)
        if (evo == null || evo.isDomainGuardrailsEnabled()) {
            for (com.example.docmind.config.DomainKnowledgePack.PolicyGuardrailRule rule : getConfiguredGuardrails()) {
                if (rule == null || !rule.isHighRisk()) {
                    continue;
                }
                if (matchesGuardrailTriggers(rule, normQ, normQ)
                        && !containsAnyNormalizedKeyword(normA, rule.getRequiredAnswerKeywords())
                        && rule.getRevisionGuidance() != null
                        && !rule.getRevisionGuidance().isBlank()) {
                    revisions.add(rule.getRevisionGuidance().strip());
                    normA = CustomerScenario.normalizeWordsPadded(draftAnswer + " " + String.join(" ", revisions));
                }
            }
        }

        // 2. Kiểm duyệt Đa lĩnh vực dựa trực tiếp trên các đoạn tài liệu RAG vừa truy xuất (RAG-Grounded Fact & Safety Critic)
        if (TOPOLOGY_AFLOW_CRITIC.equals(topology) && revisions.isEmpty() && fusedChunks != null && !fusedChunks.isEmpty()) {
            List<String> ragRevisions = extractMissingCriticalChunkFacts(normQ, normA, fusedChunks);
            for (String ragRev : ragRevisions) {
                if (!revisions.contains(ragRev)) {
                    revisions.add(ragRev);
                    normA = CustomerScenario.normalizeWordsPadded(draftAnswer + " " + String.join(" ", revisions));
                }
            }
        }

        // 3. Đảm bảo trả lời đủ các kịch bản phụ trong câu hỏi đa ý định (Multi-Intent Secondary Scenarios)
        if (secondaryScenarios != null && !secondaryScenarios.isEmpty()) {
            for (CustomerScenario sec : secondaryScenarios) {
                if (sec == CustomerScenario.WARRANTY_RETURN
                        && !(normA.contains(" bao hanh ") || normA.contains(" doi tra ")
                        || normA.contains(" hoan tien ") || normA.contains(" 7 ngay ") || normA.contains(" 14 ngay "))) {
                    String ragWarrantySentence = findChunkSentenceMatching(fusedChunks,
                            List.of("bao hanh", "doi tra", "hoan tien", "1 doi 1"));
                    revisions.add(ragWarrantySentence != null
                            ? ragWarrantySentence
                            : "Về chính sách bảo hành & đổi trả: Hệ thống hỗ trợ đổi trả trong 07 ngày (khi còn nguyên tem niêm phong) và bảo hành 1 đổi 1 hoặc hoàn tiền trong 14 ngày nếu phát sinh lỗi kỹ thuật, vận chuyển hoặc kích ứng.");
                } else if (sec == CustomerScenario.ORDER_BILLING
                        && !(normA.contains(" thanh toan ") || normA.contains(" vat ")
                        || normA.contains(" hoa don ") || normA.contains(" hoa toc ") || normA.contains(" van chuyen "))) {
                    String ragBillingSentence = findChunkSentenceMatching(fusedChunks,
                            List.of("thanh toan", "hoa don", "vat", "van chuyen", "giao hang"));
                    revisions.add(ragBillingSentence != null
                            ? ragBillingSentence
                            : "Về thanh toán & vận chuyển: Hệ thống hỗ trợ thanh toán thẻ/QR Pay/chuyển khoản/COD, miễn phí giao hỏa tốc 2 giờ nội thành và xuất hóa đơn điện tử e-VAT chính danh trong 24 giờ.");
                }
            }
        }

        if (revisions.isEmpty()) {
            return draftAnswer;
        }

        aflowCriticRevisions.incrementAndGet();
        return draftAnswer.strip() + "\n\n" + String.join("\n\n", revisions);
    }

    /**
     * Tự động đối chiếu câu trả lời nháp với các đoạn tài liệu RAG ({@code fusedChunks}) của bất kỳ lĩnh vực nào
     * để phát hiện các câu chứa cảnh báo an toàn, chống chỉ định, cam kết SLA hoặc thông số bắt buộc bị bỏ sót.
     */
    private List<String> extractMissingCriticalChunkFacts(String normQ,
                                                          String normA,
                                                          List<ReciprocalRankFusion.FusedChunk> fusedChunks) {
        List<String> criticalMarkers = List.of(
                "chong chi dinh", "khong su dung", "khong duoc", "tuyet doi khong", "luu y",
                "bat buoc", "bao hanh", "doi tra", "hoan tien", "cam ket", "sla", "uptime", "boi thuong"
        );
        List<String> missingFacts = new ArrayList<>();
        int maxChunks = Math.min(3, fusedChunks.size());

        for (int i = 0; i < maxChunks && missingFacts.size() < 2; i++) {
            ReciprocalRankFusion.FusedChunk fc = fusedChunks.get(i);
            if (fc == null || fc.chunk() == null || fc.chunk().text() == null) {
                continue;
            }
            for (String rawSentence : fc.chunk().text().split("(?<=[.!?\\n])\\s+")) {
                String cleanSentence = sanitizeRuleText(rawSentence);
                if (cleanSentence.length() < 25 || cleanSentence.startsWith("#")) {
                    continue;
                }
                String normSent = CustomerScenario.normalizeWordsPadded(cleanSentence);
                if (!containsAnyNormalizedKeyword(normSent, criticalMarkers)) {
                    continue;
                }
                // Kiểm tra xem các con số hoặc từ khoá cảnh báo chính trong câu tài liệu đã xuất hiện trong câu trả lời nháp chưa
                List<String> numericTokens = new ArrayList<>();
                for (String tok : normSent.strip().split("\\s+")) {
                    if (tok.matches(".*\\d+.*")) {
                        numericTokens.add(tok);
                    }
                }
                boolean missingNumber = !numericTokens.isEmpty()
                        && numericTokens.stream().noneMatch(num -> normA.contains(" " + num + " "));
                boolean missingContraindication = (normSent.contains(" chong chi dinh ") || normSent.contains(" tuyet doi khong "))
                        && !(normA.contains(" chong chi dinh ") || normA.contains(" tuyet doi khong ") || normA.contains(" khong su dung "));

                if (missingNumber || missingContraindication) {
                    missingFacts.add(truncate(cleanSentence, 260));
                    if (missingFacts.size() >= 2) {
                        break;
                    }
                }
            }
        }
        return missingFacts;
    }

    private String findChunkSentenceMatching(List<ReciprocalRankFusion.FusedChunk> fusedChunks,
                                             List<String> targetKeywords) {
        if (fusedChunks == null || fusedChunks.isEmpty()) {
            return null;
        }
        for (ReciprocalRankFusion.FusedChunk fc : fusedChunks) {
            if (fc == null || fc.chunk() == null || fc.chunk().text() == null) {
                continue;
            }
            for (String rawSentence : fc.chunk().text().split("(?<=[.!?\\n])\\s+")) {
                String cleanSentence = sanitizeRuleText(rawSentence);
                if (cleanSentence.length() >= 25
                        && containsAnyNormalizedKeyword(CustomerScenario.normalizeWordsPadded(cleanSentence), targetKeywords)) {
                    return truncate(cleanSentence, 260);
                }
            }
        }
        return null;
    }

    private List<com.example.docmind.config.DomainKnowledgePack.PolicyGuardrailRule> getConfiguredGuardrails() {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo != null && !evo.isDomainGuardrailsEnabled()) {
            return List.of();
        }
        if (evo != null && evo.getPolicyGuardrails() != null) {
            return evo.getPolicyGuardrails();
        }
        return com.example.docmind.config.DomainKnowledgePack.loadDefault().getPolicyGuardrails();
    }

    private com.example.docmind.config.DomainKnowledgePack.PolicyGuardrailRule findConfiguredGuardrail(String ruleId) {
        if (ruleId == null || ruleId.isBlank()) {
            return null;
        }
        for (com.example.docmind.config.DomainKnowledgePack.PolicyGuardrailRule r : getConfiguredGuardrails()) {
            if (r != null && ruleId.equals(r.getId())) {
                return r;
            }
        }
        return null;
    }

    private boolean matchesAnyHighRiskGuardrail(String normQ) {
        for (com.example.docmind.config.DomainKnowledgePack.PolicyGuardrailRule r : getConfiguredGuardrails()) {
            if (r != null && r.isHighRisk() && matchesGuardrailTriggers(r, normQ, normQ)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesGuardrailTriggers(
            com.example.docmind.config.DomainKnowledgePack.PolicyGuardrailRule rule,
            String normQ,
            String normCombined) {
        if (rule == null || rule.getQuestionTriggers() == null || rule.getQuestionTriggers().isEmpty()) {
            return false;
        }
        String target = rule.isEvaluateOnCurrentQuestionOnly() ? normQ : normCombined;
        if (!containsAnyNormalizedKeyword(target, rule.getQuestionTriggers())) {
            return false;
        }
        if (rule.getSecondaryTriggers() != null && !rule.getSecondaryTriggers().isEmpty()) {
            return containsAnyNormalizedKeyword(target, rule.getSecondaryTriggers());
        }
        return true;
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
        String cleanA = truncate(
                sanitizeRuleText(com.example.docmind.service.CustomerChatService.stripCustomerCitationMarkers(answer)),
                960
        );
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

    public Path resolveExistingStoragePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        String cleaned = rawPath.strip().replace('\\', '/');
        Path direct = Path.of(cleaned);

        if (direct.isAbsolute()) {
            try {
                if (Files.isRegularFile(direct)) {
                    return direct.normalize();
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        List<Path> candidates = new ArrayList<>();
        // 1. Direct path (relative to current working directory)
        candidates.add(direct);

        // 2. Monorepo root -> api-java subfolder
        candidates.add(Path.of("api-java", cleaned));

        // 3. Running inside api-java while path starts with api-java/
        if (cleaned.startsWith("api-java/")) {
            candidates.add(Path.of(cleaned.substring("api-java/".length())));
        }

        // 4. Running from deeper subfolder or sibling modules
        candidates.add(Path.of("..", cleaned));
        candidates.add(Path.of("..", "api-java", cleaned));

        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath().normalize();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public Path resolveWritableStoragePath(String rawPath) {
        if (rawPath != null && !rawPath.isBlank()) {
            String cleaned = rawPath.strip().replace('\\', '/');
            Path direct = Path.of(cleaned);
            if (direct.isAbsolute()) {
                return direct.normalize();
            }
        }
        if (resolvedStoragePath != null) {
            return resolvedStoragePath;
        }
        Path existing = resolveExistingStoragePath(rawPath);
        if (existing != null) {
            this.resolvedStoragePath = existing;
            return existing;
        }
        if (rawPath == null || rawPath.isBlank()) {
            return Path.of("data/customer-evolution-memory.json").toAbsolutePath().normalize();
        }
        String cleaned = rawPath.strip().replace('\\', '/');
        Path direct = Path.of(cleaned);
        if (Files.isDirectory(Path.of("api-java"))) {
            return Path.of("api-java", cleaned).toAbsolutePath().normalize();
        }
        return direct.toAbsolutePath().normalize();
    }

    public void loadStateFromDisk() {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo == null || !evo.isEnabled() || evo.getStoragePath() == null || evo.getStoragePath().isBlank()) {
            return;
        }
        synchronized (stateLock) {
            try {
                String configuredPath = evo.getStoragePath().strip();
                Path direct = Path.of(configuredPath.replace('\\', '/'));
                Path existingFile = resolveExistingStoragePath(configuredPath);

                // Chỉ tìm kiếm các đường dẫn mặc định khi configuredPath là đường dẫn tương đối
                if (existingFile == null && !direct.isAbsolute()
                        && (configuredPath.endsWith("customer-evolution-memory.json") || configuredPath.isBlank())) {
                    existingFile = resolveExistingStoragePath("data/customer-evolution-memory.json");
                    if (existingFile == null) {
                        existingFile = resolveExistingStoragePath("api-java/data/customer-evolution-memory.json");
                    }
                }

                CustomerEvolutionStatusResponse snapshot = null;
                if (existingFile != null && Files.isRegularFile(existingFile)) {
                    this.resolvedStoragePath = existingFile;
                    snapshot = objectMapper.readValue(existingFile.toFile(), CustomerEvolutionStatusResponse.class);
                    log.info("Loaded EvoAgentX customer evolution memory from disk: {}", existingFile);
                } else if (!direct.isAbsolute()) {
                    String resPath = configuredPath.replace('\\', '/');
                    if (resPath.startsWith("/")) {
                        resPath = resPath.substring(1);
                    }
                    var is = getClass().getClassLoader().getResourceAsStream(resPath);
                    if (is == null && !resPath.startsWith("data/")) {
                        is = getClass().getClassLoader().getResourceAsStream("data/" + resPath);
                    }
                    if (is == null && configuredPath.endsWith("customer-evolution-memory.json")) {
                        is = getClass().getClassLoader().getResourceAsStream("customer-evolution-memory.json");
                    }
                    java.io.InputStream stream = is;
                    if (stream != null) {
                        try (stream) {
                            snapshot = objectMapper.readValue(stream, CustomerEvolutionStatusResponse.class);
                            log.info("Loaded EvoAgentX customer evolution memory from classpath resource: {}", resPath);
                        }
                    }
                }

                if (snapshot != null) {
                    evolutionGeneration.set(Math.max(0, snapshot.evolutionGeneration()));
                    totalInteractions.set(Math.max(0L, snapshot.totalInteractions()));
                    totalFeedbackCount.set(Math.max(0L, snapshot.totalFeedbackCount()));
                    gepaCrossoverCount.set(Math.max(0, snapshot.gepaCrossoverCount()));
                    aflowCriticRevisions.set(Math.max(0L, snapshot.aflowCriticRevisions()));
                    cumulativeQualityScore = snapshot.averageQualityScore() * snapshot.totalInteractions();

                    if (snapshot.learnedGuidelines() != null) {
                        snapshot.learnedGuidelines().forEach((k, v) -> {
                            if (k != null && v != null) {
                                String code = k.toUpperCase(Locale.ROOT);
                                learnedGuidelines.put(code, new ArrayList<>(v));
                                recomputeParetoFrontier(code);
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
                    if (snapshot.learnedQueryExpansions() != null && !snapshot.learnedQueryExpansions().isEmpty()) {
                        learnedQueryExpansions.putAll(snapshot.learnedQueryExpansions());
                    }
                    if (snapshot.topBoostedChunks() != null) {
                        for (ChunkUtilityInfo cu : snapshot.topBoostedChunks()) {
                            if (cu != null && cu.chunkId() != null) {
                                chunkUtilityScores.put(cu.chunkId(), cu);
                            }
                        }
                    }
                    if (snapshot.workflowTopologyStats() != null && !snapshot.workflowTopologyStats().isEmpty()) {
                        workflowTopologyStats.putAll(snapshot.workflowTopologyStats());
                    }
                    log.info("Loaded EvoAgentX customer evolution memory (gen={}, interactions={}, avgQuality={}, gepaCrossovers={}, synonyms={})",
                            evolutionGeneration.get(), totalInteractions.get(), snapshot.averageQualityScore(),
                            gepaCrossoverCount.get(), learnedQueryExpansions.size());
                }
            } catch (Exception e) {
                log.warn("Không thể đọc bộ nhớ tự tiến hoá từ {}: {}", evo.getStoragePath(), e.getMessage());
            }
        }
    }

    private void loadFromDiskIfEnabled() {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo == null || !evo.isEnabled() || !evo.isPersistToDisk()) {
            return;
        }
        loadStateFromDisk();
    }

    private void persistToDiskIfEnabled() {
        CustomerSupportProperties.Evolution evo = customerProps.getEvolution();
        if (evo == null || !evo.isPersistToDisk() || evo.getStoragePath() == null || evo.getStoragePath().isBlank()) {
            return;
        }
        try {
            Path target = resolveWritableStoragePath(evo.getStoragePath().strip());
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
            CustomerEvolutionStatusResponse snapshot = getStatus();
            objectMapper.writeValue(tmp.toFile(), snapshot);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            this.resolvedStoragePath = target;
        } catch (Exception e) {
            log.warn("Không thể lưu bộ nhớ tự tiến hoá xuống {}: {}", evo.getStoragePath(), e.getMessage());
        }
    }

    private static String sanitizeRuleText(String s) {
        if (s == null) {
            return "";
        }
        String cleaned = s.replaceAll("(?iu)<\\s*/?\\s*(document_context|store_policy_context|conversation_history|evolved_guidelines|evolved_guideline)[^>]*>?", "")
                .replaceAll("(?iu)#+\\s*(vai\\s+trò|quy\\s+tắc|kịch\\s+bản|mẫu\\s+hội\\s+thoại|câu\\s+hỏi|tri\\s+thức)", "")
                .replaceAll("(?iu)câu\\s+hỏi\\s*:", "nội dung hỏi:")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .strip();
        return com.example.docmind.service.CustomerChatService.stripCustomerCitationMarkers(cleaned);
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        if (t.length() <= maxLen) {
            return t;
        }
        int lastPunct = Math.max(
                t.lastIndexOf(". ", maxLen),
                Math.max(t.lastIndexOf("! ", maxLen), t.lastIndexOf("? ", maxLen))
        );
        if (lastPunct >= maxLen * 2 / 3) {
            return t.substring(0, lastPunct + 1).strip();
        }
        return t.substring(0, maxLen) + "…";
    }

    private enum ImplicitSignal {
        POSITIVE,
        NEGATIVE,
        NEUTRAL
    }

    private record EvaluationResult(
            double qualityScore,
            String lossCritique,
            String textualGradient,
            double safetyPolicyScore,
            double naturalnessToneScore,
            double completenessActionScore
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


package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Trạng thái và tri thức đã học của động cơ tự tiến hoá EvoAgentX sau các lần chat với khách hàng
 * (GET /api/customer-chat/evolution, POST /api/customer-chat/feedback, POST /api/customer-chat/evolve).
 *
 * <p>Tích hợp toàn diện 6 trụ cột tiến hoá:
 * <ul>
 *   <li><b>TextGrad</b>: Đạo hàm văn bản đa tiêu chí</li>
 *   <li><b>MIPRO</b>: Chắt lọc mẫu Few-Shot chất lượng cao</li>
 *   <li><b>Long-Term Memory</b>: Chống trùng lặp SHA-256 & củng cố tri thức</li>
 *   <li><b>GEPA (Genetic-Pareto Prompt Evolution)</b>: Lai ghép di truyền & chọn lọc biên Pareto 3 trục</li>
 *   <li><b>Self-Evolving RAG</b>: Tự học từ đồng nghĩa/slang & cộng điểm uy tín Chunk (Chunk Utility Boost)</li>
 *   <li><b>AFlow Adaptive Topology & Self-Critic</b>: Định tuyến DAG động & tự kiểm duyệt 2 bước</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CustomerEvolutionStatusResponse(
        @JsonProperty("enabled")
        boolean enabled,

        @JsonProperty("strategy")
        String strategy,

        @JsonProperty("evolution_generation")
        int evolutionGeneration,

        @JsonProperty("total_interactions")
        long totalInteractions,

        @JsonProperty("total_feedback_count")
        long totalFeedbackCount,

        @JsonProperty("average_quality_score")
        double averageQualityScore,

        @JsonProperty("learned_guidelines")
        Map<String, List<LearnedGuidelineInfo>> learnedGuidelines,

        @JsonProperty("evolved_few_shot_examples")
        Map<String, List<EvolvedExampleInfo>> evolvedFewShotExamples,

        @JsonProperty("recent_gradients")
        List<TextualGradientInfo> recentGradients,

        @JsonProperty("gepa_pareto_front_size")
        int gepaParetoFrontSize,

        @JsonProperty("gepa_crossover_count")
        int gepaCrossoverCount,

        @JsonProperty("learned_synonyms_count")
        int learnedSynonymsCount,

        @JsonProperty("boosted_chunks_count")
        int boostedChunksCount,

        @JsonProperty("aflow_critic_revisions")
        long aflowCriticRevisions,

        @JsonProperty("learned_query_expansions")
        Map<String, String> learnedQueryExpansions,

        @JsonProperty("top_boosted_chunks")
        List<ChunkUtilityInfo> topBoostedChunks,

        @JsonProperty("workflow_topology_stats")
        Map<String, Long> workflowTopologyStats
) {

    /**
     * Constructor 9 tham số giữ tương thích ngược 100% cho các unit/controller test hiện hữu.
     */
    public CustomerEvolutionStatusResponse(
            boolean enabled,
            String strategy,
            int evolutionGeneration,
            long totalInteractions,
            long totalFeedbackCount,
            double averageQualityScore,
            Map<String, List<LearnedGuidelineInfo>> learnedGuidelines,
            Map<String, List<EvolvedExampleInfo>> evolvedFewShotExamples,
            List<TextualGradientInfo> recentGradients
    ) {
        this(
                enabled,
                strategy,
                evolutionGeneration,
                totalInteractions,
                totalFeedbackCount,
                averageQualityScore,
                learnedGuidelines,
                evolvedFewShotExamples,
                recentGradients,
                countParetoOptimal(learnedGuidelines),
                0,
                0,
                0,
                0L,
                Map.of(),
                List.of(),
                Map.of()
        );
    }

    private static int countParetoOptimal(Map<String, List<LearnedGuidelineInfo>> guidelines) {
        if (guidelines == null || guidelines.isEmpty()) {
            return 0;
        }
        return (int) guidelines.values().stream()
                .filter(list -> list != null)
                .flatMap(List::stream)
                .filter(g -> g != null && g.paretoOptimal())
                .count();
    }

    /**
     * Một quy tắc hướng dẫn (Guideline) được đúc kết và tiến hoá qua TextGrad & GEPA Pareto.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LearnedGuidelineInfo(
            @JsonProperty("id")
            String id,

            @JsonProperty("scenario")
            String scenario,

            @JsonProperty("rule")
            String rule,

            @JsonProperty("source_gradient")
            String sourceGradient,

            @JsonProperty("confidence_score")
            double confidenceScore,

            @JsonProperty("reinforcement_count")
            int reinforcementCount,

            @JsonProperty("content_hash")
            String contentHash,

            @JsonProperty("updated_at")
            String updatedAt,

            @JsonProperty("generation_method")
            String generationMethod,

            @JsonProperty("pareto_optimal")
            boolean paretoOptimal,

            @JsonProperty("pareto_rank")
            int paretoRank,

            @JsonProperty("safety_policy_score")
            double safetyPolicyScore,

            @JsonProperty("naturalness_tone_score")
            double naturalnessToneScore,

            @JsonProperty("completeness_action_score")
            double completenessActionScore
    ) {
        /**
         * Constructor 8 tham số giữ tương thích ngược cho các test hiện hữu.
         */
        public LearnedGuidelineInfo(
                String id,
                String scenario,
                String rule,
                String sourceGradient,
                double confidenceScore,
                int reinforcementCount,
                String contentHash,
                String updatedAt
        ) {
            this(
                    id,
                    scenario,
                    rule,
                    sourceGradient,
                    confidenceScore,
                    reinforcementCount,
                    contentHash,
                    updatedAt,
                    "TEXTGRAD_MUTATION",
                    true,
                    1,
                    confidenceScore,
                    confidenceScore,
                    confidenceScore
            );
        }
    }

    /**
     * Một mẫu hội thoại Few-Shot (Exemplar) chất lượng cao được chắt lọc theo nguyên lý MIPRO của EvoAgentX.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EvolvedExampleInfo(
            @JsonProperty("id")
            String id,

            @JsonProperty("scenario")
            String scenario,

            @JsonProperty("customer_question")
            String customerQuestion,

            @JsonProperty("assistant_answer")
            String assistantAnswer,

            @JsonProperty("quality_score")
            double qualityScore,

            @JsonProperty("reinforcement_count")
            int reinforcementCount,

            @JsonProperty("content_hash")
            String contentHash,

            @JsonProperty("created_at")
            String createdAt
    ) {
    }

    /**
     * Bản ghi phản tư Textual Gradient (Loss Critique + Gradient + Action) của 1 lần chat với khách hàng.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TextualGradientInfo(
            @JsonProperty("interaction_id")
            String interactionId,

            @JsonProperty("scenario")
            String scenario,

            @JsonProperty("question_excerpt")
            String questionExcerpt,

            @JsonProperty("quality_score")
            double qualityScore,

            @JsonProperty("loss_critique")
            String lossCritique,

            @JsonProperty("textual_gradient")
            String textualGradient,

            @JsonProperty("applied_action")
            String appliedAction,

            @JsonProperty("timestamp")
            String timestamp
    ) {
    }

    /**
     * Thông tin điểm uy tín của Chunk tài liệu trong cơ chế Self-Evolving RAG (Adaptive Chunk Utility Boosting).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChunkUtilityInfo(
            @JsonProperty("chunk_id")
            String chunkId,

            @JsonProperty("document_id")
            String documentId,

            @JsonProperty("heading_summary")
            String headingSummary,

            @JsonProperty("helpful_count")
            int helpfulCount,

            @JsonProperty("unhelpful_count")
            int unhelpfulCount,

            @JsonProperty("utility_boost")
            double utilityBoost,

            @JsonProperty("updated_at")
            String updatedAt
    ) {
    }
}


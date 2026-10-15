package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Trạng thái và tri thức đã học của động cơ tự tiến hoá EvoAgentX sau các lần chat với khách hàng
 * (GET /api/customer-chat/evolution, POST /api/customer-chat/feedback, POST /api/customer-chat/evolve).
 */
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
        List<TextualGradientInfo> recentGradients
) {

    /**
     * Một quy tắc hướng dẫn (Guideline) được đúc kết và tiến hoá qua TextGrad sau các lần chat.
     */
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
            String updatedAt
    ) {
    }

    /**
     * Một mẫu hội thoại Few-Shot (Exemplar) chất lượng cao được chắt lọc theo nguyên lý MIPRO của EvoAgentX.
     */
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
}

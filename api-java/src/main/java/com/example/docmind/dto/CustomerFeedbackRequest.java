package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Request gửi đánh giá / phản hồi của khách hàng sau một lần chat để kích hoạt
 * vòng lặp tự tiến hoá (EvoAgentX TextGrad & MIPRO feedback loop).
 *
 * <p>Khách hàng hoặc hệ thống có thể truyền {@code interaction_id} (nhận được từ
 * {@link CustomerChatResponse#interactionId()}) hoặc truyền trực tiếp cặp {@code question} / {@code answer}.
 */
public record CustomerFeedbackRequest(
        @JsonProperty("interaction_id")
        @JsonAlias({"interactionId", "episode_id", "episodeId"})
        String interactionId,

        @JsonProperty("question")
        @JsonAlias({"message", "query", "customer_message", "customerMessage"})
        String question,

        @JsonProperty("answer")
        @JsonAlias({"response", "assistant_answer", "assistantAnswer"})
        String answer,

        @JsonProperty("scenario")
        @JsonAlias({"intent", "category"})
        String scenario,

        @JsonProperty("rating")
        @Min(value = 1, message = "rating tối thiểu là 1")
        @Max(value = 5, message = "rating tối đa là 5")
        Integer rating,

        @JsonProperty("helpful")
        @JsonAlias({"is_helpful", "isHelpful", "satisfied"})
        Boolean helpful,

        @JsonProperty("feedback")
        @JsonAlias({"comment", "correction", "critique", "user_feedback", "userFeedback"})
        String feedback
) {

    @JsonIgnore
    @AssertTrue(message = "Cần cung cấp interaction_id hoặc question của lần chat để đánh giá")
    public boolean isTargetProvided() {
        return (interactionId != null && !interactionId.isBlank())
                || (question != null && !question.isBlank());
    }
}

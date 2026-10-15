package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response của API tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước
 * kết hợp chiến lược tự tiến hoá EvoAgentX.
 *
 * <p>Bao gồm câu trả lời tư vấn, thông tin vai trò khách hàng, kịch bản prompt đã kích hoạt,
 * gợi ý câu hỏi tiếp theo, danh sách citation (nếu có tài liệu RAG) và thông tin phiên
 * tự tiến hoá ({@code interaction_id}, {@code evolution_generation}, {@code applied_learned_guidelines}).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerChatResponse(
        String answer,

        @JsonProperty("customer_role")
        String customerRole,

        @JsonProperty("scenario")
        String scenario,

        @JsonProperty("prompt_template_id")
        String promptTemplateId,

        @JsonProperty("model")
        String model,

        @JsonProperty("suggested_questions")
        List<String> suggestedQuestions,

        List<Citation> citations,

        @JsonProperty("interaction_id")
        String interactionId,

        @JsonProperty("evolution_generation")
        Integer evolutionGeneration,

        @JsonProperty("applied_learned_guidelines")
        Integer appliedLearnedGuidelines
) {

    /**
     * Constructor tương thích ngược 7 tham số cho các caller/test hiện hữu.
     */
    public CustomerChatResponse(
            String answer,
            String customerRole,
            String scenario,
            String promptTemplateId,
            String model,
            List<String> suggestedQuestions,
            List<Citation> citations) {
        this(answer, customerRole, scenario, promptTemplateId, model,
                suggestedQuestions, citations, null, 0, 0);
    }
}

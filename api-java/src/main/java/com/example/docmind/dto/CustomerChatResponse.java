package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response của API tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước.
 * Bao gồm câu trả lời tư vấn, thông tin vai trò khách hàng, kịch bản prompt đã kích hoạt,
 * gợi ý câu hỏi tiếp theo và danh sách citation (nếu có tài liệu RAG).
 */
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

        List<Citation> citations
) {
}

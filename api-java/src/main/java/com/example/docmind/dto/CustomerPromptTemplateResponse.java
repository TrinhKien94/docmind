package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Thông tin chi tiết về bộ prompt được huấn luyện trước cho nghiệp vụ tư vấn khách hàng
 * mua hàng hoặc sử dụng dịch vụ (GET /api/customer-chat/prompts).
 */
public record CustomerPromptTemplateResponse(
        @JsonProperty("prompt_template_id")
        String promptTemplateId,

        @JsonProperty("store_name")
        String storeName,

        @JsonProperty("customer_role_description")
        String customerRoleDescription,

        @JsonProperty("assistant_persona")
        String assistantPersona,

        @JsonProperty("scenarios")
        List<ScenarioInfo> scenarios,

        @JsonProperty("sample_prompt_preview")
        String samplePromptPreview
) {
    public record ScenarioInfo(
            @JsonProperty("code")
            String code,

            @JsonProperty("title")
            String title,

            @JsonProperty("description")
            String description,

            @JsonProperty("suggested_questions")
            List<String> suggestedQuestions
    ) {
    }
}

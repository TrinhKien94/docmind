package com.example.docmind.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request cho API tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước
 * (POST /api/customer-chat, POST /api/chat/customer, POST /api/customer-support/chat).
 *
 * <p>Người dùng đóng vai trò là khách hàng mua hàng hoặc sử dụng dịch vụ.
 * Hỗ trợ truyền câu hỏi qua trường {@code question}, {@code message} hoặc {@code query}.
 * Trường {@code document_ids} là tuỳ chọn: nếu truyền sẽ RAG trên các tài liệu đó;
 * nếu để trống hệ thống tự động tìm tài liệu READY hoặc trả lời dựa trên prompt huấn luyện trước.
 */
public record CustomerChatRequest(
        @JsonProperty("question")
        String question,

        @JsonProperty("message")
        @JsonAlias({"content", "text", "input", "user_message", "userMessage", "customer_message", "customerMessage"})
        String message,

        @JsonProperty("query")
        @JsonAlias({"prompt", "ask"})
        String query,

        @JsonProperty("customer_name")
        @JsonAlias({"customerName", "user_name", "userName"})
        String customerName,

        @JsonProperty("customer_role")
        @JsonAlias({"customerRole", "role"})
        String customerRole,

        @JsonProperty("scenario")
        @JsonAlias({"intent", "prompt_type", "promptType", "category"})
        String scenario,

        @JsonProperty("document_ids")
        @JsonAlias({"documentIds"})
        List<@NotBlank(message = "document_id trong danh sách không được rỗng") String> documentIds,

        @JsonProperty("top_k")
        @JsonAlias({"topK"})
        @Min(value = 1, message = "top_k tối thiểu 1")
        @Max(value = 50, message = "top_k tối đa 50")
        Integer topK,

        @JsonProperty("history")
        @JsonAlias({"messages", "conversation_history", "conversationHistory"})
        List<@Valid ConversationTurn> history,

        @JsonProperty("custom_context")
        @JsonAlias({"customContext", "context", "product_context", "productContext"})
        String customContext
) {

    /** Constructor rút gọn cho trường hợp chỉ truyền câu hỏi. */
    public CustomerChatRequest(String question) {
        this(question, null, null, null, null, null, null, null, null, null);
    }

    /** Constructor tiện lợi cho test và gọi nội bộ. */
    public CustomerChatRequest(String question, String scenario, List<String> documentIds, Integer topK) {
        this(question, null, null, null, null, scenario, documentIds, topK, null, null);
    }

    /**
     * Lấy nội dung câu hỏi/tin nhắn của khách hàng từ question -> message -> query.
     */
    @JsonIgnore
    public String resolvedQuestion() {
        if (question != null && !question.isBlank()) {
            return question.strip();
        }
        if (message != null && !message.isBlank()) {
            return message.strip();
        }
        if (query != null && !query.isBlank()) {
            return query.strip();
        }
        return "";
    }

    @JsonIgnore
    @AssertTrue(message = "question hoặc message của khách hàng không được để trống")
    public boolean isQuestionProvided() {
        return !resolvedQuestion().isEmpty();
    }

    /** Một lượt hội thoại trong lịch sử chat của khách hàng. */
    public record ConversationTurn(
            @JsonProperty("role")
            @NotBlank(message = "role trong history không được rỗng")
            String role,

            @JsonProperty("content")
            @NotBlank(message = "content trong history không được rỗng")
            String content
    ) {
    }
}

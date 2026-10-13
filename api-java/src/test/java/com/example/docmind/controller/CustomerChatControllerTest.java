package com.example.docmind.controller;

import com.example.docmind.dto.Citation;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerPromptTemplateResponse;
import com.example.docmind.service.CustomerChatService;
import com.example.docmind.service.retrieval.CustomerScenario;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Controller slice test cho API tư vấn khách hàng dùng prompt huấn luyện trước. */
@WebMvcTest(CustomerChatController.class)
class CustomerChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerChatService customerChatService;

    @Test
    void customerChatReturnsAnswerScenarioAndCitations() throws Exception {
        Citation citation = new Citation(
                "chunk-sales-1", "doc-catalog-1", List.of("block-1"),
                1, List.of(50.0, 60.0, 400.0, 120.0),
                List.of("Bảng giá", "Gói Pro"),
                "Gói Pro có giá 499.000đ/tháng, hỗ trợ OCR đa ngôn ngữ.");

        CustomerChatResponse mockResp = new CustomerChatResponse(
                "Dạ em chào Anh Minh! Gói Pro hiện có giá 499.000đ/tháng [1].",
                "Khách hàng mua hàng",
                "SALES",
                "customer-sales-service-v1",
                "9router:codex/gpt-6-astra",
                CustomerScenario.SALES.suggestedQuestions(),
                List.of(citation)
        );
        when(customerChatService.chat(any())).thenReturn(mockResp);

        String requestJson = """
                {
                  "question": "Tư vấn cho tôi gói Pro giá bao nhiêu?",
                  "customer_name": "Anh Minh",
                  "customer_role": "Khách hàng mua hàng",
                  "scenario": "SALES",
                  "document_ids": ["doc-catalog-1"],
                  "top_k": 5
                }
                """;

        mockMvc.perform(post("/api/customer-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Dạ em chào Anh Minh! Gói Pro hiện có giá 499.000đ/tháng [1]."))
                .andExpect(jsonPath("$.customer_role").value("Khách hàng mua hàng"))
                .andExpect(jsonPath("$.scenario").value("SALES"))
                .andExpect(jsonPath("$.prompt_template_id").value("customer-sales-service-v1"))
                .andExpect(jsonPath("$.model").value("9router:codex/gpt-6-astra"))
                .andExpect(jsonPath("$.suggested_questions").isArray())
                .andExpect(jsonPath("$.citations[0].chunk_id").value("chunk-sales-1"));
    }

    @Test
    void customerChatSupportsMessageAliasAndOptionalDocumentIdsOnAliasRoutes() throws Exception {
        CustomerChatResponse mockResp = new CustomerChatResponse(
                "Dạ tài khoản dịch vụ được kích hoạt ngay sau khi thanh toán ạ.",
                "Khách hàng mua hàng hoặc sử dụng dịch vụ",
                "SERVICE_SUPPORT",
                "customer-sales-service-v1",
                "9router:codex/gpt-6-astra",
                CustomerScenario.SERVICE_SUPPORT.suggestedQuestions(),
                List.of()
        );
        when(customerChatService.chat(any())).thenReturn(mockResp);

        String requestJson = """
                {
                  "message": "Mình mới đăng ký dịch vụ thì kích hoạt như thế nào?"
                }
                """;

        mockMvc.perform(post("/api/chat/customer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("SERVICE_SUPPORT"))
                .andExpect(jsonPath("$.answer").isNotEmpty());

        mockMvc.perform(post("/api/customer-support/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("SERVICE_SUPPORT"));
    }

    @Test
    void customerChatRejectsBlankQuestionAndMessageWith400() throws Exception {
        String body = """
                {
                  "question": "   ",
                  "message": ""
                }
                """;
        mockMvc.perform(post("/api/customer-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerChatRejectsInvalidTopKWith400() throws Exception {
        String body = """
                {
                  "question": "Tôi muốn mua gói Pro",
                  "top_k": 0
                }
                """;
        mockMvc.perform(post("/api/customer-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerChatRejectsMalformedOrEmptyJsonWith400Not500() throws Exception {
        mockMvc.perform(post("/api/customer-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/customer-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void customerChatSupportsContentAndPromptFieldAliases() throws Exception {
        CustomerChatResponse mockResp = new CustomerChatResponse(
                "Dạ gói Pro phù hợp cho nhóm 10 người ạ.",
                "Khách hàng mua hàng hoặc sử dụng dịch vụ",
                "SALES",
                "customer-sales-service-v1",
                "9router:codex/gpt-6-astra",
                CustomerScenario.SALES.suggestedQuestions(),
                List.of()
        );
        when(customerChatService.chat(any())).thenReturn(mockResp);

        mockMvc.perform(post("/api/customer-chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\": \"Tư vấn gói Pro cho công ty tôi\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario").value("SALES"));
    }

    @Test
    void getPromptTemplatesReturnsPretrainedScenariosAndPreview() throws Exception {
        CustomerPromptTemplateResponse templateResp = new CustomerPromptTemplateResponse(
                "customer-sales-service-v1",
                "DocMind Store & Digital Services",
                "Khách hàng mua hàng hoặc sử dụng dịch vụ",
                "Chuyên viên Tư vấn Bán hàng & Chăm sóc Khách hàng",
                CustomerScenario.allScenarioInfos(),
                "# VAI TRÒ\nBạn là Chuyên viên Tư vấn..."
        );
        when(customerChatService.getPromptTemplates()).thenReturn(templateResp);

        mockMvc.perform(get("/api/customer-chat/prompts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prompt_template_id").value("customer-sales-service-v1"))
                .andExpect(jsonPath("$.store_name").value("DocMind Store & Digital Services"))
                .andExpect(jsonPath("$.scenarios").isArray())
                .andExpect(jsonPath("$.scenarios.length()").value(5))
                .andExpect(jsonPath("$.sample_prompt_preview").isNotEmpty());
    }
}

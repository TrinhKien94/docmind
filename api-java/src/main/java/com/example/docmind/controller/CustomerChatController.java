package com.example.docmind.controller;

import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerEvolutionStatusResponse;
import com.example.docmind.dto.CustomerFeedbackRequest;
import com.example.docmind.dto.CustomerPromptTemplateResponse;
import com.example.docmind.service.CustomerChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller cho API tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước
 * kết hợp chiến lược tự tiến hoá học hỏi sau các lần chat (EvoAgentX TextGrad & MIPRO).
 *
 * <p>Hỗ trợ các đường dẫn chuẩn:
 * <ul>
 *   <li>{@code POST   /api/customer-chat} (chat tư vấn khách hàng mua hàng / sử dụng dịch vụ)</li>
 *   <li>{@code GET    /api/customer-chat/prompts} (xem thông tin bộ prompt & kịch bản được huấn luyện trước)</li>
 *   <li>{@code GET    /api/customer-chat/evolution} (xem trạng thái tự tiến hoá & các quy tắc đã học)</li>
 *   <li>{@code POST   /api/customer-chat/feedback} (gửi đánh giá/phản hồi để kích hoạt học hỏi TextGrad)</li>
 *   <li>{@code POST   /api/customer-chat/evolve} (kích hoạt chủ động chu kỳ tối ưu hoá EvoAgentX)</li>
 *   <li>{@code DELETE /api/customer-chat/evolution} (đặt lại bộ nhớ tiến hoá)</li>
 * </ul>
 */
@RestController
public class CustomerChatController {

    private final CustomerChatService customerChatService;

    public CustomerChatController(CustomerChatService customerChatService) {
        this.customerChatService = customerChatService;
    }

    @PostMapping({
            "/api/customer-chat",
            "/api/chat/customer",
            "/api/customer-support/chat"
    })
    public ResponseEntity<CustomerChatResponse> chatAsCustomer(
            @Valid @RequestBody CustomerChatRequest request) {
        return ResponseEntity.ok(customerChatService.chat(request));
    }

    @GetMapping({
            "/api/customer-chat/prompts",
            "/api/chat/customer/prompts",
            "/api/customer-support/prompts"
    })
    public ResponseEntity<CustomerPromptTemplateResponse> getPromptTemplates() {
        return ResponseEntity.ok(customerChatService.getPromptTemplates());
    }

    @GetMapping({
            "/api/customer-chat/evolution",
            "/api/chat/customer/evolution",
            "/api/customer-support/evolution"
    })
    public ResponseEntity<CustomerEvolutionStatusResponse> getEvolutionStatus() {
        return ResponseEntity.ok(customerChatService.getEvolutionStatus());
    }

    @PostMapping({
            "/api/customer-chat/feedback",
            "/api/chat/customer/feedback",
            "/api/customer-support/feedback"
    })
    public ResponseEntity<CustomerEvolutionStatusResponse> submitCustomerFeedback(
            @Valid @RequestBody CustomerFeedbackRequest feedbackRequest) {
        return ResponseEntity.ok(customerChatService.submitFeedback(feedbackRequest));
    }

    @PostMapping({
            "/api/customer-chat/evolve",
            "/api/chat/customer/evolve",
            "/api/customer-support/evolve"
    })
    public ResponseEntity<CustomerEvolutionStatusResponse> triggerEvolution(
            @RequestParam(value = "scenario", required = false) String scenario,
            @RequestBody(required = false) Map<String, Object> body) {
        String resolvedScenario = scenario;
        if ((resolvedScenario == null || resolvedScenario.isBlank()) && body != null) {
            Object scObj = body.get("scenario");
            if (scObj instanceof String s && !s.isBlank()) {
                resolvedScenario = s.strip();
            }
        }
        return ResponseEntity.ok(customerChatService.triggerEvolution(resolvedScenario));
    }

    @DeleteMapping({
            "/api/customer-chat/evolution",
            "/api/chat/customer/evolution",
            "/api/customer-support/evolution"
    })
    public ResponseEntity<CustomerEvolutionStatusResponse> resetEvolution() {
        return ResponseEntity.ok(customerChatService.resetEvolution());
    }
}

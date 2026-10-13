package com.example.docmind.controller;

import com.example.docmind.dto.CustomerChatRequest;
import com.example.docmind.dto.CustomerChatResponse;
import com.example.docmind.dto.CustomerPromptTemplateResponse;
import com.example.docmind.service.CustomerChatService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller cho API tư vấn & chăm sóc khách hàng sử dụng prompt được huấn luyện trước.
 * Người dùng đóng vai trò là khách hàng mua hàng hoặc sử dụng dịch vụ.
 *
 * <p>Hỗ trợ các đường dẫn chuẩn:
 * <ul>
 *   <li>{@code POST /api/customer-chat} (chính)</li>
 *   <li>{@code POST /api/chat/customer} (alias)</li>
 *   <li>{@code POST /api/customer-support/chat} (alias)</li>
 *   <li>{@code GET  /api/customer-chat/prompts} (xem thông tin bộ prompt & kịch bản được huấn luyện trước)</li>
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
}

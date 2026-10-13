package com.example.docmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình tích hợp 9Router / OpenAI-compatible gateway tới Codex (GPT-6 Astra).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "ninerouter")
public class NineRouterProperties {

    /** Bật/tắt sử dụng 9router làm LLM client. Mặc định true. */
    private boolean enabled = true;

    /** Base URL của 9router (OpenAI compatible). */
    private String baseUrl = "http://127.0.0.1:20128/v1";

    /** API Key của 9router. */
    private String apiKey = "sk-9af495182557ea7e-713jdu-0feaf415";

    /** Model gọi qua 9router (mặc định là codex/gpt-6-astra). */
    private String model = "codex/gpt-6-astra";

    /** Direct Codex Relay URL fallback nếu 9router không phản hồi. */
    private String codexRelayUrl = "http://127.0.0.1:11500/v1";

    /** Optional Codex Auth Token override (hữu ích trong Docker/CI). */
    private String codexAuthToken = "";

    /** Timeout (ms) cho LLM generation. */
    private int timeoutMs = 60000;
}

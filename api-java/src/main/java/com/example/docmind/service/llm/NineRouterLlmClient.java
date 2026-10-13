package com.example.docmind.service.llm;

import com.example.docmind.config.NineRouterEnabledCondition;
import com.example.docmind.config.NineRouterProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * NineRouterLlmClient — Tích hợp DocMind chat với Codex hiện tại (GPT-6 Astra) qua 9Router gateway.
 * Kích hoạt mặc định (@Primary) khi ninerouter.enabled=true.
 * Hỗ trợ fallback tự động sang direct Codex relay (127.0.0.1:11500) nếu 9router gặp sự cố.
 */
@Component
@Primary
@Conditional(NineRouterEnabledCondition.class)
public class NineRouterLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(NineRouterLlmClient.class);

    private final NineRouterProperties props;
    private final RestClient http;
    private final ObjectMapper objectMapper;

    public NineRouterLlmClient(NineRouterProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(props.getTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(props.getTimeoutMs()));

        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();

        log.info("NineRouterLlmClient active (baseUrl={}, model={})",
                props.getBaseUrl(), props.getModel());
    }

    @Override
    public String complete(String prompt) {
        // 1) Thử gọi qua 9Router
        try {
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            Map<String, Object> resp = http.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + props.getApiKey())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            String answer = extractText(resp);
            if (answer != null && !answer.isBlank()) {
                log.info("Called 9Router successfully via model={}", props.getModel());
                return answer;
            }
        } catch (Exception e) {
            log.warn("9Router invocation failed ({}), trying direct Codex relay fallback...", e.getMessage());
        }

        // 2) Fallback: Gọi trực tiếp sang Codex relay (127.0.0.1:11500)
        try {
            String token = resolveCodexAuthToken();
            if (token != null) {
                RestClient directRelay = RestClient.builder()
                        .baseUrl(props.getCodexRelayUrl())
                        .build();

                Map<String, Object> body = Map.of(
                        "model", "gpt-6-astra",
                        "messages", List.of(
                                Map.of("role", "user", "content", prompt)
                        )
                );

                Map<String, Object> resp = directRelay.post()
                        .uri("/chat/completions")
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(Map.class);

                String answer = extractText(resp);
                if (answer != null && !answer.isBlank()) {
                    log.info("Direct Codex relay invocation succeeded with gpt-6-astra");
                    return answer;
                }
            }
        } catch (Exception e) {
            log.error("Direct Codex relay fallback also failed: {}", e.getMessage());
        }

        return "[9router-error] Không thể kết nối tới 9Router hoặc Codex relay.";
    }

    @Override
    public String name() {
        return "9router:" + props.getModel();
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> resp) {
        if (resp == null) {
            return null;
        }
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message != null && message.get("content") != null) {
                return message.get("content").toString().trim();
            }
        } catch (Exception e) {
            log.warn("Error parsing 9Router response: {}", e.getMessage());
        }
        return null;
    }

    private String resolveCodexAuthToken() {
        try {
            String userHome = System.getProperty("user.home");
            Path authPath = Path.of(userHome, ".codex", "auth.json");
            if (Files.exists(authPath)) {
                JsonNode root = objectMapper.readTree(authPath.toFile());
                JsonNode tokenNode = root.path("tokens").path("access_token");
                if (!tokenNode.isMissingNode() && !tokenNode.asText().isBlank()) {
                    return tokenNode.asText().trim();
                }
            }
        } catch (Exception e) {
            log.warn("Could not read access token from ~/.codex/auth.json: {}", e.getMessage());
        }
        return null;
    }
}

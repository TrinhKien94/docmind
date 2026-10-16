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
import org.springframework.web.client.RestClientResponseException;

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
    private final RestClient directRelay;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public NineRouterLlmClient(NineRouterProperties props) {
        this(props, new ObjectMapper());
    }

    public NineRouterLlmClient(NineRouterProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(props.getTimeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(props.getTimeoutMs()));

        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();

        this.directRelay = RestClient.builder()
                .baseUrl(props.getCodexRelayUrl())
                .requestFactory(requestFactory)
                .build();

        log.info("NineRouterLlmClient active (baseUrl={}, model={}, timeoutMs={})",
                props.getBaseUrl(), props.getModel(), props.getTimeoutMs());
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String prompt) {
        String completionsUri = (props.getChatCompletionsPath() != null && !props.getChatCompletionsPath().isBlank())
                ? props.getChatCompletionsPath().strip()
                : "/chat/completions";

        // 1) Thử gọi qua 9Router
        try {
            Map<String, Object> body = Map.of(
                    "model", props.getModel(),
                    "messages", List.of(
                            Map.of("role", "user", "content", prompt)
                    )
            );

            var requestSpec = http.post()
                    .uri(completionsUri)
                    .header("Content-Type", "application/json");

            if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
                requestSpec.header("Authorization", "Bearer " + props.getApiKey().trim());
            }

            Map<String, Object> resp = requestSpec.body(body)
                    .retrieve()
                    .body(Map.class);

            String answer = extractText(resp);
            if (answer != null && !answer.isBlank()) {
                log.info("Called 9Router successfully via model={}", props.getModel());
                return answer;
            }
        } catch (RestClientResponseException e) {
            log.warn("9Router HTTP error {} (body={}), trying direct Codex relay fallback...",
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("9Router invocation failed ({}), trying direct Codex relay fallback...", e.getMessage());
        }

        // 2) Fallback: Gọi trực tiếp sang Codex relay (127.0.0.1:11500)
        try {
            String token = resolveCodexAuthToken();
            if (token != null) {
                String directModel = props.getModel();
                if (directModel.contains("/")) {
                    directModel = directModel.substring(directModel.lastIndexOf('/') + 1);
                }

                Map<String, Object> body = Map.of(
                        "model", directModel,
                        "messages", List.of(
                                Map.of("role", "user", "content", prompt)
                        )
                );

                Map<String, Object> resp = directRelay.post()
                        .uri(completionsUri)
                        .header("Authorization", "Bearer " + token)
                        .header("Content-Type", "application/json")
                        .body(body)
                        .retrieve()
                        .body(Map.class);

                String answer = extractText(resp);
                if (answer != null && !answer.isBlank()) {
                    log.info("Direct Codex relay invocation succeeded with model={}", directModel);
                    return answer;
                }
            } else {
                log.warn("Direct Codex relay fallback skipped: no access token available");
            }
        } catch (RestClientResponseException e) {
            log.error("Direct Codex relay HTTP error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429
                    && props.getBaseUrl() != null
                    && !props.getBaseUrl().contains("20128")) {
                try {
                    RestClient emergencyRouter = RestClient.builder()
                            .baseUrl("http://127.0.0.1:20128/v1")
                            .build();
                    Map<String, Object> body = Map.of(
                            "model", props.getModel(),
                            "messages", List.of(Map.of("role", "user", "content", prompt))
                    );
                    Map<String, Object> resp = emergencyRouter.post()
                            .uri(completionsUri)
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer sk-9af495182557ea7e-713jdu-0feaf415")
                            .body(body)
                            .retrieve()
                            .body(Map.class);
                    String answer = extractText(resp);
                    if (answer != null && !answer.isBlank()) {
                        log.info("Emergency 9Router multi-account pool succeeded after direct relay 429");
                        return answer;
                    }
                } catch (Exception ignored) {
                    // Fall through to fallback error message
                }
            }
        } catch (Exception e) {
            log.error("Direct Codex relay fallback also failed: {}", e.getMessage());
        }

        return (props.getFallbackErrorMessage() != null && !props.getFallbackErrorMessage().isBlank())
                ? props.getFallbackErrorMessage().strip()
                : "[9router-error] Không thể kết nối tới 9Router hoặc Codex relay.";
    }

    @Override
    public String name() {
        return "9router:" + props.getModel();
    }

    @SuppressWarnings("unchecked")
    public String extractText(Map<String, Object> resp) {
        if (resp == null) {
            return null;
        }
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) resp.get("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            Map<String, Object> firstChoice = choices.get(0);
            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
            if (message != null) {
                Object contentObj = message.get("content");
                if (contentObj instanceof String str && !str.isBlank()) {
                    return str.trim();
                } else if (contentObj instanceof List<?> parts) {
                    StringBuilder sb = new StringBuilder();
                    for (Object p : parts) {
                        if (p instanceof Map<?, ?> partMap) {
                            Object text = partMap.get("text");
                            if (text != null) {
                                sb.append(text);
                            }
                        }
                    }
                    if (!sb.isEmpty()) {
                        return sb.toString().trim();
                    }
                }
            }
            Object textObj = firstChoice.get("text");
            if (textObj instanceof String s && !s.isBlank()) {
                return s.trim();
            }
        } catch (Exception e) {
            log.warn("Error parsing 9Router response: {}", e.getMessage());
        }
        return null;
    }

    private String resolveCodexAuthToken() {
        if (props.getCodexAuthToken() != null && !props.getCodexAuthToken().isBlank()) {
            return props.getCodexAuthToken().trim();
        }
        String envToken = System.getenv("CODEX_AUTH_TOKEN");
        if (envToken != null && !envToken.isBlank()) {
            return envToken.trim();
        }
        try {
            String configuredPath = (props.getCodexAuthFilePath() != null && !props.getCodexAuthFilePath().isBlank())
                    ? props.getCodexAuthFilePath().strip()
                    : ".codex/auth.json";
            Path candidate = Path.of(configuredPath);
            Path authPath = candidate.isAbsolute()
                    ? candidate
                    : Path.of(System.getProperty("user.home")).resolve(candidate);
            if (Files.exists(authPath)) {
                JsonNode root = objectMapper.readTree(authPath.toFile());
                JsonNode tokenNode = root.path("tokens").path("access_token");
                if (!tokenNode.isMissingNode() && !tokenNode.asText().isBlank()) {
                    return tokenNode.asText().trim();
                }
            }
        } catch (Exception e) {
            log.warn("Could not read access token from codex auth file: {}", e.getMessage());
        }
        return null;
    }
}

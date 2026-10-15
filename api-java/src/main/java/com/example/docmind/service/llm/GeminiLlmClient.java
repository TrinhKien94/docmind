package com.example.docmind.service.llm;

import com.example.docmind.config.GeminiEnabledCondition;
import com.example.docmind.config.GeminiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * GeminiLlmClient — CHỈ kích hoạt khi gemini.api.keys có giá trị và 9Router tắt.
 */
@Component
@Primary
@Conditional(GeminiEnabledCondition.class)
public class GeminiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiLlmClient.class);

    private final GeminiProperties props;
    private final RestClient http;
    private int rrIndex = 0;

    public GeminiLlmClient(GeminiProperties props) {
        this.props = props;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(
                props.getConnectTimeoutMs() > 0 ? props.getConnectTimeoutMs() : 10000));
        requestFactory.setReadTimeout(Duration.ofMillis(
                props.getReadTimeoutMs() > 0 ? props.getReadTimeoutMs() : 60000));

        this.http = RestClient.builder()
                .baseUrl(props.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
        log.info("GeminiLlmClient active (model={}, keys={})",
                props.getModel(), props.getApi().getKeys().size());
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String prompt) {
        String key = nextKey();
        if (key == null) {
            log.warn("Không có Gemini key hợp lệ -> trả câu trả lời fallback.");
            return "[gemini-unavailable] Chưa cấu hình được Gemini key hợp lệ.";
        }
        String pathTemplate = (props.getGeneratePathTemplate() != null && !props.getGeneratePathTemplate().isBlank())
                ? props.getGeneratePathTemplate().strip()
                : "/models/{model}:generateContent?key={key}";
        try {
            Map<String, Object> body = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            );
            Map<String, Object> resp = http.post()
                    .uri(pathTemplate, props.getModel(), key)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return extractText(resp);
        } catch (Exception e) {
            log.error("Gọi Gemini thất bại, fallback: {}", e.getMessage());
            return "[gemini-error] Không gọi được Gemini: " + e.getMessage();
        }
    }

    @Override
    public String name() {
        return "gemini:" + props.getModel();
    }

    private synchronized String nextKey() {
        List<String> keys = props.getApi().getKeys();
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(rrIndex % keys.size());
            rrIndex = (rrIndex + 1) % keys.size();
            if (k != null && !k.isBlank()) {
                return k.trim();
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> resp) {
        if (resp == null) {
            return "[gemini-empty]";
        }
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) resp.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                return "[gemini-empty]";
            }
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> part : parts) {
                Object text = part.get("text");
                if (text != null) {
                    sb.append(text);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Parse Gemini response lỗi: {}", e.getMessage());
            return "[gemini-parse-error]";
        }
    }
}

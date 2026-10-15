package com.example.docmind.service.llm;

import com.example.docmind.config.NineRouterProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NineRouterLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testClientNameReflectsModel() {
        NineRouterProperties props = new NineRouterProperties();
        props.setModel("codex/gpt-6-astra");
        props.setBaseUrl("http://127.0.0.1:20128/v1");

        NineRouterLlmClient client = new NineRouterLlmClient(props, objectMapper);
        assertThat(client.name()).isEqualTo("9router:codex/gpt-6-astra");
    }

    @Test
    void testExtractTextFormats() {
        NineRouterProperties props = new NineRouterProperties();
        NineRouterLlmClient client = new NineRouterLlmClient(props, objectMapper);

        // 1. Standard text content
        Map<String, Object> resp1 = Map.of("choices", List.of(
                Map.of("message", Map.of("content", "  Hello world  "))
        ));
        assertThat(client.extractText(resp1)).isEqualTo("Hello world");

        // 2. Multipart content list
        Map<String, Object> resp2 = Map.of("choices", List.of(
                Map.of("message", Map.of("content", List.of(
                        Map.of("type", "text", "text", "Part A "),
                        Map.of("type", "text", "text", "Part B")
                )))
        ));
        assertThat(client.extractText(resp2)).isEqualTo("Part A Part B");

        // 3. Legacy completion text format
        Map<String, Object> resp3 = Map.of("choices", List.of(
                Map.of("text", "Legacy completion text")
        ));
        assertThat(client.extractText(resp3)).isEqualTo("Legacy completion text");

        // 4. Null / empty responses
        assertThat(client.extractText(null)).isNull();
        assertThat(client.extractText(Map.of())).isNull();
        assertThat(client.extractText(Map.of("choices", List.of()))).isNull();
    }

    @Test
    void testFallbackWhenBothEndpointsUnreachable() {
        NineRouterProperties props = new NineRouterProperties();
        props.setBaseUrl("http://127.0.0.1:59999/v1"); // unreachable
        props.setCodexRelayUrl("http://127.0.0.1:59998/v1"); // unreachable
        props.setTimeoutMs(500);

        NineRouterLlmClient client = new NineRouterLlmClient(props, objectMapper);
        String result = client.complete("Hello");

        // Không ném ngoại lệ làm chết luồng chat, trả thông báo an toàn
        assertThat(result).contains("[9router-error]");
    }

    @Test
    void testLiveCompletionViaNineRouterDirectly() {
        if (!isPortListening("127.0.0.1", 20128)) {
            // Bỏ qua nếu môi trường offline không chạy 9router
            return;
        }

        NineRouterProperties props = new NineRouterProperties();
        props.setBaseUrl("http://127.0.0.1:20128/v1");
        props.setApiKey("sk-9af495182557ea7e-713jdu-0feaf415");
        props.setModel("codex/gpt-6-astra");
        // Đặt codexRelayUrl tới cổng không tồn tại để chứng minh kết quả là THẬT từ 9Router,
        // không phải do rơi vào fallback của direct relay.
        props.setCodexRelayUrl("http://127.0.0.1:59998/v1");
        // GPT-6 Astra reasoning cần thời gian đủ dài
        props.setTimeoutMs(45000);

        NineRouterLlmClient client = new NineRouterLlmClient(props, objectMapper);
        String answer = client.complete("Trả lời chính xác một từ duy nhất: OK");

        assertThat(answer).isNotBlank();
        assertThat(answer).doesNotContain("[9router-error]");
    }

    @Test
    void testFallbackToDirectRelayWhenNineRouterFails() {
        if (!isPortListening("127.0.0.1", 11500)) {
            // Bỏ qua nếu direct relay không bật
            return;
        }

        NineRouterProperties props = new NineRouterProperties();
        props.setBaseUrl("http://127.0.0.1:59999/v1"); // NineRouter unreachble
        props.setCodexRelayUrl("http://127.0.0.1:11500/v1");
        props.setModel("codex/gpt-6-astra");
        props.setTimeoutMs(45000);

        NineRouterLlmClient client = new NineRouterLlmClient(props, objectMapper);
        String answer = client.complete("Trả lời chính xác một từ duy nhất: OK");

        assertThat(answer).isNotBlank();
        assertThat(answer).doesNotContain("[9router-error]");
    }

    private static boolean isPortListening(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 600);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

package com.example.docmind.service.llm;

import com.example.docmind.config.NineRouterProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
    void testFallbackWhenEndpointUnreachable() {
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
    void testLiveCompletionIfNineRouterRunning() {
        NineRouterProperties props = new NineRouterProperties();
        props.setBaseUrl("http://127.0.0.1:20128/v1");
        props.setApiKey("sk-9af495182557ea7e-713jdu-0feaf415");
        props.setModel("codex/gpt-6-astra");
        props.setTimeoutMs(10000);

        NineRouterLlmClient client = new NineRouterLlmClient(props, objectMapper);
        try {
            String answer = client.complete("Trả lời chính xác một từ duy nhất: OK");
            if (!answer.contains("[9router-error]")) {
                assertThat(answer).isNotBlank();
            }
        } catch (Exception e) {
            // Trường hợp 9router chưa bật trong môi trường CI/build offline
        }
    }
}

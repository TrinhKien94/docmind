package com.example.docmind.config;

import com.example.docmind.service.llm.GeminiLlmClient;
import com.example.docmind.service.llm.LlmClient;
import com.example.docmind.service.llm.NineRouterLlmClient;
import com.example.docmind.service.llm.StubLlmClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withUserConfiguration(
                    NineRouterProperties.class,
                    GeminiProperties.class,
                    NineRouterLlmClient.class,
                    GeminiLlmClient.class,
                    StubLlmClient.class
            );

    @Test
    void whenNineRouterEnabledByDefault_onlyNineRouterClientIsActive() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(LlmClient.class);
            assertThat(context.getBean(LlmClient.class)).isInstanceOf(NineRouterLlmClient.class);
            assertThat(context).doesNotHaveBean(GeminiLlmClient.class);
            assertThat(context).doesNotHaveBean(StubLlmClient.class);
        });
    }

    @Test
    void whenBothNineRouterAndGeminiKeyConfigured_nineRouterTakesPrecedenceWithoutConflict() {
        // Đây chính là kịch bản trước đây gây NoUniqueBeanDefinitionException
        contextRunner
                .withPropertyValues("gemini.api.keys=test-key-123", "ninerouter.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(NineRouterLlmClient.class);
                    assertThat(context).doesNotHaveBean(GeminiLlmClient.class);
                });
    }

    @Test
    void whenNineRouterDisabledAndGeminiKeyPresent_geminiClientIsActive() {
        contextRunner
                .withPropertyValues("ninerouter.enabled=false", "gemini.api.keys=test-key-123")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(GeminiLlmClient.class);
                    assertThat(context).doesNotHaveBean(NineRouterLlmClient.class);
                    assertThat(context).doesNotHaveBean(StubLlmClient.class);
                });
    }

    @Test
    void whenNineRouterDisabledAndNoGeminiKey_stubLlmClientIsActive() {
        contextRunner
                .withPropertyValues("ninerouter.enabled=false", "gemini.api.keys=")
                .run(context -> {
                    assertThat(context).hasSingleBean(LlmClient.class);
                    assertThat(context.getBean(LlmClient.class)).isInstanceOf(StubLlmClient.class);
                    assertThat(context).doesNotHaveBean(NineRouterLlmClient.class);
                    assertThat(context).doesNotHaveBean(GeminiLlmClient.class);
                });
    }
}

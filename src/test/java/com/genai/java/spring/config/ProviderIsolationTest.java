package com.genai.java.spring.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProviderIsolationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(ProviderProperties.class, ProviderProperties::new)
            .withUserConfiguration(
                    AIProviderConfig.VertexAiProviderConfiguration.class,
                    AIProviderConfig.HuggingFaceProviderConfiguration.class,
                    AIProviderConfig.OllamaProviderConfiguration.class);

    @Test
    @DisplayName("@spec:AC-005 Optional providers do not prevent local startup")
    void optionalProvidersDoNotPreventLocalStartup() {
        contextRunner
                .withPropertyValues("app.ai.provider=openai")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean("vertexAIChatClient");
                    assertThat(context).doesNotHaveBean("huggingFaceChatClient");
                    assertThat(context).doesNotHaveBean("ollamaChatClient");
                    assertThat(context.getBean(ProviderProperties.class).getCohere().isEnabled()).isFalse();
                });

        contextRunner
                .withPropertyValues("app.ai.provider=ollama")
                .withBean(OllamaChatModel.class, () -> mock(OllamaChatModel.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("ollamaChatClient");
                    assertThat(context).doesNotHaveBean("vertexAIChatClient");
                    assertThat(context).doesNotHaveBean("huggingFaceChatClient");
                });
    }
}

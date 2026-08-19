package com.genai.java.spring.aiagent;

import com.genai.java.spring.aiagent.config.data.AIAgentConfigData;
import com.genai.java.spring.aiagent.tools.posture.PostureTools;
import com.genai.java.spring.aiagent.tools.posture.records.PostureArgs;
import com.genai.java.spring.aiagent.tools.web.GcpTokenProvider;
import com.genai.java.spring.aiagent.tools.web.WebTools;
import com.genai.java.spring.aiagent.tools.web.records.WebArgs;
import com.genai.java.spring.config.ProviderProperties;
import com.genai.java.spring.rag.config.data.RagConfigData;
import com.genai.java.spring.rag.rerank.client.RerankerClient;
import com.genai.java.spring.rag.rerank.exception.RerankException;
import com.genai.java.spring.rag.rerank.processor.RerankPostProcessor;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundToolResilienceTest {

    private static final String INTERNAL_DETAIL = "upstream-secret-stack-detail";

    @Test
    @DisplayName("@spec:AC-012 External calls have a timeout, limited retries and sanitized errors")
    void externalCallsHaveTimeoutLimitedRetriesAndSanitizedErrors() {
        ProviderProperties providerProperties = providerProperties(Duration.ofMillis(300), 2);
        AIAgentConfigData agentConfig = agentConfig();

        AtomicInteger webAttempts = new AtomicInteger();
        WebTools webTools = new WebTools(
                failingWebClient(webAttempts),
                tokenProvider(),
                agentConfig,
                ObservationRegistry.NOOP,
                providerProperties);

        // Warm up Reactor/Netty classes so the assertion measures the configured call policy,
        // not one-time JVM class loading in a cold CI container.
        webTools.search(new WebArgs("warmup", 1));
        webAttempts.set(0);

        long webStartedAt = System.nanoTime();
        Map<String, Object> webResult = webTools.search(new WebArgs("authentication", 3));
        Duration webDuration = Duration.ofNanos(System.nanoTime() - webStartedAt);

        assertThat(webDuration).isLessThan(Duration.ofSeconds(1));
        assertThat(webAttempts).hasValue(2);
        assertThat(webResult).containsEntry("error", "WEB_SEARCH_FAILED");
        assertThat(webResult.toString()).doesNotContain(INTERNAL_DETAIL);

        AtomicInteger postureAttempts = new AtomicInteger();
        PostureTools postureTools = new PostureTools(
                neverCompletingWebClient(postureAttempts), agentConfig, providerProperties);

        long postureStartedAt = System.nanoTime();
        Map<String, Object> postureResult = postureTools.getSecurityPosture(new PostureArgs("payments"));
        Duration postureDuration = Duration.ofNanos(System.nanoTime() - postureStartedAt);

        assertThat(postureDuration).isLessThan(Duration.ofSeconds(1));
        assertThat(postureAttempts.get()).isBetween(1, providerProperties.getOutbound().getMaxAttempts());
        assertThat(postureResult).containsEntry("error", "POSTURE_SERVICE_CALL_FAILED");
        assertThat(postureResult.toString()).doesNotContain(INTERNAL_DETAIL);

        AtomicInteger rerankAttempts = new AtomicInteger();
        RerankerClient slowReranker = (query, documents) -> {
            rerankAttempts.incrementAndGet();
            try {
                Thread.sleep(Duration.ofSeconds(5));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RerankException("interrupted " + INTERNAL_DETAIL, interrupted);
            }
            return new double[documents.size()];
        };
        List<Document> originalDocuments = List.of(new Document("first"), new Document("second"));
        RerankPostProcessor postProcessor = new RerankPostProcessor(
                slowReranker, ragConfig(), providerPropertiesWithCohereEnabled(providerProperties));

        long rerankStartedAt = System.nanoTime();
        List<Document> reranked = postProcessor.process(new Query("question"), originalDocuments);
        Duration rerankDuration = Duration.ofNanos(System.nanoTime() - rerankStartedAt);

        assertThat(rerankDuration).isLessThan(Duration.ofSeconds(1));
        assertThat(rerankAttempts.get()).isBetween(1, providerProperties.getOutbound().getMaxAttempts());
        assertThat(reranked).isSameAs(originalDocuments);
    }

    private static ProviderProperties providerProperties(Duration timeout, int maxAttempts) {
        ProviderProperties properties = new ProviderProperties();
        properties.getOutbound().setTimeout(timeout);
        properties.getOutbound().setMaxAttempts(maxAttempts);
        properties.getOutbound().setRetryBackoff(Duration.ofMillis(10));
        return properties;
    }

    private static ProviderProperties providerPropertiesWithCohereEnabled(ProviderProperties properties) {
        properties.getCohere().setEnabled(true);
        return properties;
    }

    private static WebClient.Builder failingWebClient(AtomicInteger attempts) {
        ExchangeFunction exchange = request -> Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(INTERNAL_DETAIL)
                    .build());
        });
        return WebClient.builder().exchangeFunction(exchange);
    }

    private static WebClient.Builder neverCompletingWebClient(AtomicInteger attempts) {
        ExchangeFunction exchange = request -> Mono.defer(() -> {
            attempts.incrementAndGet();
            return Mono.never();
        });
        return WebClient.builder().exchangeFunction(exchange);
    }

    private static GcpTokenProvider tokenProvider() {
        return new GcpTokenProvider() {
            @Override
            public String getAccessTokenValue() {
                return "test-token";
            }
        };
    }

    private static AIAgentConfigData agentConfig() {
        AIAgentConfigData config = new AIAgentConfigData();

        AIAgentConfigData.PostureToolProperties posture = new AIAgentConfigData.PostureToolProperties();
        posture.setUrl("http://posture.test/api/posture/{id}");
        posture.setEnv("test");
        config.setPostureTool(posture);

        AIAgentConfigData.GoogleVertexSearch vertex = new AIAgentConfigData.GoogleVertexSearch();
        vertex.setEndpointBaseUrl("https://vertex.test");
        vertex.setServingConfig("projects/test/servingConfigs/default");

        AIAgentConfigData.Owasp owasp = new AIAgentConfigData.Owasp();
        owasp.setCheatSheetProtocol("https://");
        owasp.setCheatSheetUrl("cheatsheetseries.owasp.org/");
        owasp.setAsvsUrl("https://owasp.org/asvs");

        AIAgentConfigData.WebToolProperties web = new AIAgentConfigData.WebToolProperties();
        web.setTopK(3);
        web.setGoogleVertexSearch(vertex);
        web.setOwasp(owasp);
        config.setWebTool(web);
        return config;
    }

    private static RagConfigData ragConfig() {
        RagConfigData config = new RagConfigData();
        RagConfigData.RerankProperties rerank = new RagConfigData.RerankProperties();
        rerank.setTopN(2);
        config.setRerank(rerank);
        return config;
    }
}

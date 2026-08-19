package com.genai.java.spring.aiagent.tools.posture;

import com.genai.java.spring.aiagent.config.data.AIAgentConfigData;
import com.genai.java.spring.aiagent.tools.posture.records.PostureArgs;
import com.genai.java.spring.config.ProviderProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.Collections;
import java.util.Map;

@Slf4j
@Component
public class PostureTools {
    private final WebClient webClient;
    private final AIAgentConfigData.PostureToolProperties postureToolProperties;
    private final ProviderProperties.Outbound outbound;

    public PostureTools(WebClient.Builder builder,
                        AIAgentConfigData aiAgentConfigData,
                        ProviderProperties providerProperties) {
        this.webClient = builder.baseUrl(aiAgentConfigData.getPostureTool().getUrl()).build();
        this.postureToolProperties = aiAgentConfigData.getPostureTool();
        this.outbound = providerProperties.getOutbound();
    }

    @Tool(name = "security_posture", description = "Get security posture for a service (internetFacing, data classes, TLS, vulnerabilities, secrets).")
    public Map<String, Object> getSecurityPosture(PostureArgs postureArgs) {
        try {
            if (postureArgs == null) {
                return Collections.emptyMap();
            }
            log.info("Calling security_posture tool with service id: {}", postureArgs.serviceId());
            return execute(this.webClient.get()
                    .uri(uri -> uri.queryParam("env", postureToolProperties.getEnv()).build(postureArgs.serviceId()))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {
                    }))
                    .block();
        } catch (Exception e) {
            log.warn("security_posture external call failed: {}", e.getClass().getSimpleName());
            return Map.of(
                    "error", "POSTURE_SERVICE_CALL_FAILED",
                    "message", "Security posture service is temporarily unavailable");
        }
    }

    private <T> Mono<T> execute(Mono<T> request) {
        Mono<T> retried = request;
        int retries = outbound.getMaxAttempts() - 1;
        if (retries > 0) {
            retried = request.retryWhen(Retry.fixedDelay(retries, outbound.getRetryBackoff())
                    .filter(PostureTools::isRetryable)
                    .onRetryExhaustedThrow((retrySpec, signal) -> signal.failure()));
        }
        return retried.timeout(outbound.getTimeout());
    }

    private static boolean isRetryable(Throwable failure) {
        return failure instanceof WebClientRequestException
                || failure instanceof WebClientResponseException response
                && response.getStatusCode().is5xxServerError();
    }
}

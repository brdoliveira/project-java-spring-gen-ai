package com.genai.java.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app.ai")
public class ProviderProperties {

    private String provider = "openai";
    private final Cohere cohere = new Cohere();
    private final Outbound outbound = new Outbound();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
    }

    public Cohere getCohere() {
        return cohere;
    }

    public Outbound getOutbound() {
        return outbound;
    }

    public static final class Cohere {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static final class Outbound {
        private Duration timeout = Duration.ofSeconds(5);
        private int maxAttempts = 2;
        private Duration retryBackoff = Duration.ofMillis(100);

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = requirePositive(timeout, "timeout");
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            if (maxAttempts < 1 || maxAttempts > 5) {
                throw new IllegalArgumentException("maxAttempts must be between 1 and 5");
            }
            this.maxAttempts = maxAttempts;
        }

        public Duration getRetryBackoff() {
            return retryBackoff;
        }

        public void setRetryBackoff(Duration retryBackoff) {
            this.retryBackoff = requirePositive(retryBackoff, "retryBackoff");
        }

        private static Duration requirePositive(Duration value, String name) {
            Objects.requireNonNull(value, name + " must not be null");
            if (value.isZero() || value.isNegative()) {
                throw new IllegalArgumentException(name + " must be positive");
            }
            return value;
        }
    }
}

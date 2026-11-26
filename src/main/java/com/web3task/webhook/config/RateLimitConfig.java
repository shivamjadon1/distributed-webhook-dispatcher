package com.web3task.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "webhook.rate-limit")
public class RateLimitConfig {

    private LimiterSettings ingestion = new LimiterSettings(100, 100, 1);
    private LimiterSettings downstream = new LimiterSettings(10, 10, 1);

    public LimiterSettings getIngestion() {
        return ingestion;
    }

    public void setIngestion(LimiterSettings ingestion) {
        this.ingestion = ingestion;
    }

    public LimiterSettings getDownstream() {
        return downstream;
    }

    public void setDownstream(LimiterSettings downstream) {
        this.downstream = downstream;
    }

    public static class LimiterSettings {
        private long capacity = 100;
        private long refillTokens = 100;
        private long refillDurationSeconds = 1;

        public LimiterSettings() {
        }

        public LimiterSettings(long capacity, long refillTokens, long refillDurationSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillDurationSeconds = refillDurationSeconds;
        }

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public long getRefillDurationSeconds() {
            return refillDurationSeconds;
        }

        public void setRefillDurationSeconds(long refillDurationSeconds) {
            this.refillDurationSeconds = refillDurationSeconds;
        }
    }
}

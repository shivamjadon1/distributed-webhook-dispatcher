package com.web3task.webhook.service;

import com.web3task.webhook.config.RateLimitConfig;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private final RateLimitConfig rateLimitConfig;
    private final Map<String, Bucket> ingestionBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> downstreamBuckets = new ConcurrentHashMap<>();

    public RateLimiterService(RateLimitConfig rateLimitConfig) {
        this.rateLimitConfig = rateLimitConfig;
    }

    /**
     * Attempts to consume 1 token for an incoming ingestion request.
     * @param clientKey IP or identifier of the calling client
     * @return true if consumed, false if limit exceeded
     */
    public boolean tryConsumeIngestion(String clientKey) {
        Bucket bucket = ingestionBuckets.computeIfAbsent(clientKey, this::createIngestionBucket);
        return bucket.tryConsume(1);
    }

    /**
     * Attempts to consume 1 token for dispatching a webhook to a destination domain.
     * @param domain Target host or domain name
     * @return true if permitted, false if rate limited
     */
    public boolean tryConsumeDownstream(String domain) {
        Bucket bucket = downstreamBuckets.computeIfAbsent(domain.toLowerCase(), this::createDownstreamBucket);
        return bucket.tryConsume(1);
    }

    private Bucket createIngestionBucket(String key) {
        RateLimitConfig.LimiterSettings settings = rateLimitConfig.getIngestion();
        Bandwidth limit = Bandwidth.builder()
                .capacity(settings.getCapacity())
                .refillGreedy(settings.getRefillTokens(), Duration.ofSeconds(settings.getRefillDurationSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private Bucket createDownstreamBucket(String domain) {
        RateLimitConfig.LimiterSettings settings = rateLimitConfig.getDownstream();
        Bandwidth limit = Bandwidth.builder()
                .capacity(settings.getCapacity())
                .refillGreedy(settings.getRefillTokens(), Duration.ofSeconds(settings.getRefillDurationSeconds()))
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    public void resetAll() {
        ingestionBuckets.clear();
        downstreamBuckets.clear();
    }
}

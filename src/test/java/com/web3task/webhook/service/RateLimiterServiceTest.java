package com.web3task.webhook.service;

import com.web3task.webhook.config.RateLimitConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        RateLimitConfig config = new RateLimitConfig();
        config.setIngestion(new RateLimitConfig.LimiterSettings(3, 3, 1));
        config.setDownstream(new RateLimitConfig.LimiterSettings(2, 2, 1));
        rateLimiterService = new RateLimiterService(config);
    }

    @Test
    @DisplayName("Ingestion rate limiter permits up to capacity and rejects subsequent tokens")
    void testIngestionRateLimiting() {
        String clientIp = "198.51.100.10";

        assertTrue(rateLimiterService.tryConsumeIngestion(clientIp));
        assertTrue(rateLimiterService.tryConsumeIngestion(clientIp));
        assertTrue(rateLimiterService.tryConsumeIngestion(clientIp));
        assertFalse(rateLimiterService.tryConsumeIngestion(clientIp), "Fourth request should be blocked by rate limit");
    }

    @Test
    @DisplayName("Downstream rate limiter isolates tokens per domain")
    void testDownstreamRateLimitingPerDomain() {
        String domainA = "customer-a.example.com";
        String domainB = "customer-b.example.com";

        assertTrue(rateLimiterService.tryConsumeDownstream(domainA));
        assertTrue(rateLimiterService.tryConsumeDownstream(domainA));
        assertFalse(rateLimiterService.tryConsumeDownstream(domainA), "Third request to domain A should be blocked");

        // Domain B must have independent tokens
        assertTrue(rateLimiterService.tryConsumeDownstream(domainB));
        assertTrue(rateLimiterService.tryConsumeDownstream(domainB));
        assertFalse(rateLimiterService.tryConsumeDownstream(domainB));
    }
}

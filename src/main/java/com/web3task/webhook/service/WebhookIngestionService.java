package com.web3task.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3task.webhook.dto.WebhookDispatchRequest;
import com.web3task.webhook.dto.WebhookDispatchResponse;
import com.web3task.webhook.entity.JobStatus;
import com.web3task.webhook.entity.WebhookJob;
import com.web3task.webhook.exception.RateLimitExceededException;
import com.web3task.webhook.repository.WebhookJobRepository;
import com.web3task.webhook.security.SafeUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class WebhookIngestionService {

    private static final Logger log = LoggerFactory.getLogger(WebhookIngestionService.class);

    private final WebhookJobRepository webhookJobRepository;
    private final SafeUrlValidator safeUrlValidator;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public WebhookIngestionService(
            WebhookJobRepository webhookJobRepository,
            SafeUrlValidator safeUrlValidator,
            RateLimiterService rateLimiterService,
            ObjectMapper objectMapper) {
        this.webhookJobRepository = webhookJobRepository;
        this.safeUrlValidator = safeUrlValidator;
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates and accepts a webhook dispatch request.
     * Enforces rate limiting, SSRF safety, persists job in DB, and returns HTTP 202 immediately.
     */
    @Transactional
    public WebhookDispatchResponse ingestWebhook(WebhookDispatchRequest request, String clientIp) {
        // 1. Ingestion rate limit check
        if (!rateLimiterService.tryConsumeIngestion(clientIp != null ? clientIp : "anonymous")) {
            log.warn("Ingestion rate limit exceeded for client IP {}", clientIp);
            throw new RateLimitExceededException("Ingestion rate limit exceeded. Please throttle your requests.", 1);
        }

        // 2. Safe URL & SSRF validation
        safeUrlValidator.validateUrl(request.getDestinationUrl());

        // 3. Serialize payload safely
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request.getPayload());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to serialize request payload: " + e.getMessage());
        }

        // 4. Persist in PostgreSQL (database-backed queue)
        WebhookJob job = new WebhookJob(
                request.getEventId().trim(),
                request.getDestinationUrl().trim(),
                payloadJson
        );
        job.setStatus(JobStatus.PENDING);
        job.setNextRetryAt(Instant.now());

        WebhookJob saved = webhookJobRepository.save(job);
        log.info("Queued webhook job ID {} for eventId {} destination {}", saved.getId(), saved.getEventId(), saved.getDestinationUrl());

        // 5. Return immediate response
        return new WebhookDispatchResponse("queued", saved.getId());
    }
}

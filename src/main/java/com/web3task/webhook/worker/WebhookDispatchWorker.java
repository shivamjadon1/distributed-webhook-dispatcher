package com.web3task.webhook.worker;

import com.web3task.webhook.entity.WebhookJob;
import com.web3task.webhook.repository.WebhookJobRepository;
import com.web3task.webhook.service.RateLimiterService;
import com.web3task.webhook.service.WebhookDeliveryService;
import com.web3task.webhook.service.WebhookJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

@Component
public class WebhookDispatchWorker {

    private static final Logger log = LoggerFactory.getLogger(WebhookDispatchWorker.class);

    private static final long[] DEFAULT_BACKOFFS_MS = {2000L, 4000L, 8000L};

    private final WebhookJobRepository webhookJobRepository;
    private final WebhookJobService webhookJobService;
    private final WebhookDeliveryService webhookDeliveryService;
    private final RateLimiterService rateLimiterService;
    private final Executor taskExecutor;

    private final boolean enabled;
    private final int batchSize;
    private final int maxRetries;

    public WebhookDispatchWorker(
            WebhookJobRepository webhookJobRepository,
            WebhookJobService webhookJobService,
            WebhookDeliveryService webhookDeliveryService,
            RateLimiterService rateLimiterService,
            @Qualifier("webhookTaskExecutor") Executor taskExecutor,
            @Value("${webhook.worker.enabled:true}") boolean enabled,
            @Value("${webhook.worker.batch-size:25}") int batchSize,
            @Value("${webhook.worker.max-retries:3}") int maxRetries) {
        this.webhookJobRepository = webhookJobRepository;
        this.webhookJobService = webhookJobService;
        this.webhookDeliveryService = webhookDeliveryService;
        this.rateLimiterService = rateLimiterService;
        this.taskExecutor = taskExecutor;
        this.enabled = enabled;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
    }

    /**
     * Periodic scheduled poller that fetches due jobs from PostgreSQL
     * and dispatches them across worker threads.
     */
    @Scheduled(fixedDelayString = "${webhook.worker.polling-rate-ms:1000}")
    public void pollAndDispatch() {
        if (!enabled) {
            return;
        }

        try {
            List<String> dueJobIds = webhookJobRepository.findDueJobIds(Instant.now(), batchSize);
            if (dueJobIds.isEmpty()) {
                return;
            }

            log.debug("Found {} due webhook jobs for processing", dueJobIds.size());
            for (String jobId : dueJobIds) {
                taskExecutor.execute(() -> processJob(jobId));
            }
        } catch (Exception e) {
            log.error("Exception during webhook queue polling: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes an individual job safely.
     * Transaction 1: Claim job atomically (status -> PROCESSING).
     * External HTTP: WebClient POST without holding database locks.
     * Transaction 2: Record delivery log and update job status (SUCCESS / RETRYING / FAILED).
     */
    public void processJob(String jobId) {
        // Step 1: Claim job atomically
        boolean claimed = webhookJobService.claimJob(jobId);
        if (!claimed) {
            // Already claimed by another worker instance or thread
            return;
        }

        WebhookJob job = webhookJobService.getJob(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} claimed but entity not found in database", jobId);
            return;
        }

        // Step 2: Downstream per-domain rate limit check
        String domain = extractDomain(job.getDestinationUrl());
        if (!rateLimiterService.tryConsumeDownstream(domain)) {
            log.warn("Downstream domain '{}' rate limited. Rescheduling job {}", domain, jobId);
            webhookJobService.rescheduleForRateLimit(jobId, Instant.now().plusMillis(1000));
            return;
        }

        // Step 3: Perform external HTTP dispatch (no open DB transaction!)
        int attemptNumber = job.getAttempts() + 1;
        log.info("Executing delivery attempt {} for job {} to {}", attemptNumber, jobId, job.getDestinationUrl());

        WebhookDeliveryService.DeliveryResult result = 
                webhookDeliveryService.sendWebhook(job.getDestinationUrl(), job.getPayload());

        // Step 4: Handle response & record delivery outcome
        if (result.isSuccess()) {
            webhookJobService.recordSuccess(jobId, attemptNumber, result.statusCode(), result.responseBody());
        } else if (!result.isRetryable()) {
            // Permanent non-retryable error (e.g. 400 Bad Request, 404 Not Found)
            webhookJobService.recordPermanentFailure(
                    jobId, attemptNumber, result.statusCode(), result.errorMessage(), result.responseBody());
        } else {
            // Retryable error: 5xx, 429, timeout, network error
            if (attemptNumber > maxRetries) {
                String errorMsg = (result.errorMessage() != null ? result.errorMessage() : "Delivery failed") 
                        + " (Max retries exhausted)";
                webhookJobService.recordPermanentFailure(
                        jobId, attemptNumber, result.statusCode(), errorMsg, result.responseBody());
            } else {
                long backoffMs = calculateBackoffMs(attemptNumber, result.retryAfterSeconds());
                Instant nextRetryAt = Instant.now().plusMillis(backoffMs);
                webhookJobService.recordRetry(
                        jobId, attemptNumber, result.statusCode(), result.errorMessage(), 
                        result.responseBody(), nextRetryAt);
            }
        }
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host : "unknown-domain";
        } catch (Exception e) {
            return "unknown-domain";
        }
    }

    public long calculateBackoffMs(int attemptNumber, Long retryAfterSeconds) {
        int index = Math.min(Math.max(attemptNumber - 1, 0), DEFAULT_BACKOFFS_MS.length - 1);
        long backoff = DEFAULT_BACKOFFS_MS[index];

        if (retryAfterSeconds != null && retryAfterSeconds > 0) {
            long retryAfterMs = retryAfterSeconds * 1000L;
            return Math.max(backoff, retryAfterMs);
        }
        return backoff;
    }
}

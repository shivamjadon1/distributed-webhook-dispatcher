package com.web3task.webhook.service;

import com.web3task.webhook.entity.DeliveryLog;
import com.web3task.webhook.entity.JobStatus;
import com.web3task.webhook.entity.WebhookJob;
import com.web3task.webhook.repository.DeliveryLogRepository;
import com.web3task.webhook.repository.WebhookJobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class WebhookJobService {

    private static final Logger log = LoggerFactory.getLogger(WebhookJobService.class);

    private final WebhookJobRepository webhookJobRepository;
    private final DeliveryLogRepository deliveryLogRepository;

    public WebhookJobService(WebhookJobRepository webhookJobRepository, DeliveryLogRepository deliveryLogRepository) {
        this.webhookJobRepository = webhookJobRepository;
        this.deliveryLogRepository = deliveryLogRepository;
    }

    /**
     * Atomically claims a job for processing in an independent transaction.
     * Prevents multiple workers from claiming or processing the same job.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claimJob(String jobId) {
        int updated = webhookJobRepository.updateJobStatusIfIn(
                jobId,
                List.of(JobStatus.PENDING, JobStatus.RETRYING),
                JobStatus.PROCESSING,
                Instant.now()
        );
        return updated == 1;
    }

    @Transactional(readOnly = true)
    public Optional<WebhookJob> getJob(String jobId) {
        return webhookJobRepository.findById(jobId);
    }

    /**
     * Records successful delivery and marks job as SUCCESS.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String jobId, int attemptNumber, Integer statusCode, String responseBody) {
        DeliveryLog logEntry = new DeliveryLog(
                jobId,
                attemptNumber,
                statusCode,
                JobStatus.SUCCESS,
                null,
                responseBody
        );
        deliveryLogRepository.save(logEntry);

        webhookJobRepository.updateJobExecutionResult(
                jobId,
                JobStatus.SUCCESS,
                attemptNumber,
                null,
                Instant.now()
        );
        log.info("Webhook job {} successfully delivered on attempt {}", jobId, attemptNumber);
    }

    /**
     * Records a retryable failure, logs the attempt, and updates next_retry_at.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetry(String jobId, int attemptNumber, Integer statusCode, String errorMessage,
                            String responseBody, Instant nextRetryAt) {
        DeliveryLog logEntry = new DeliveryLog(
                jobId,
                attemptNumber,
                statusCode,
                JobStatus.RETRYING,
                errorMessage,
                responseBody
        );
        deliveryLogRepository.save(logEntry);

        webhookJobRepository.updateJobExecutionResult(
                jobId,
                JobStatus.RETRYING,
                attemptNumber,
                nextRetryAt,
                Instant.now()
        );
        log.warn("Webhook job {} scheduled for retry (attempt {}) at {}", jobId, attemptNumber, nextRetryAt);
    }

    /**
     * Records permanent failure (e.g. 4xx error or retries exhausted).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPermanentFailure(String jobId, int attemptNumber, Integer statusCode,
                                       String errorMessage, String responseBody) {
        DeliveryLog logEntry = new DeliveryLog(
                jobId,
                attemptNumber,
                statusCode,
                JobStatus.FAILED,
                errorMessage,
                responseBody
        );
        deliveryLogRepository.save(logEntry);

        webhookJobRepository.updateJobExecutionResult(
                jobId,
                JobStatus.FAILED,
                attemptNumber,
                null,
                Instant.now()
        );
        log.error("Webhook job {} permanently failed after attempt {}: {}", jobId, attemptNumber, errorMessage);
    }

    /**
     * Reschedules job due to downstream rate limit without counting as an attempt.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rescheduleForRateLimit(String jobId, Instant nextRetryAt) {
        WebhookJob job = webhookJobRepository.findById(jobId).orElse(null);
        if (job != null) {
            job.setStatus(JobStatus.RETRYING);
            job.setNextRetryAt(nextRetryAt);
            webhookJobRepository.save(job);
        }
    }
}

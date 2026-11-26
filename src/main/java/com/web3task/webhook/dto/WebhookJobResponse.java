package com.web3task.webhook.dto;

import com.web3task.webhook.entity.JobStatus;
import com.web3task.webhook.entity.WebhookJob;

import java.time.Instant;

public class WebhookJobResponse {

    private String id;
    private String eventId;
    private String destinationUrl;
    private String payload;
    private JobStatus status;
    private int attempts;
    private Instant nextRetryAt;
    private Instant createdAt;
    private Instant updatedAt;

    public WebhookJobResponse() {
    }

    public static WebhookJobResponse fromEntity(WebhookJob job) {
        WebhookJobResponse res = new WebhookJobResponse();
        res.id = job.getId();
        res.eventId = job.getEventId();
        res.destinationUrl = job.getDestinationUrl();
        res.payload = job.getPayload();
        res.status = job.getStatus();
        res.attempts = job.getAttempts();
        res.nextRetryAt = job.getNextRetryAt();
        res.createdAt = job.getCreatedAt();
        res.updatedAt = job.getUpdatedAt();
        return res;
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getDestinationUrl() {
        return destinationUrl;
    }

    public String getPayload() {
        return payload;
    }

    public JobStatus getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

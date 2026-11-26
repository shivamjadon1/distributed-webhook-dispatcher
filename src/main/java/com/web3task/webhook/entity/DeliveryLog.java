package com.web3task.webhook.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "delivery_logs",
    indexes = {
        @Index(name = "idx_delivery_logs_job_id", columnList = "job_id, attempt_number"),
        @Index(name = "idx_delivery_logs_attempted_at", columnList = "attempted_at")
    }
)
public class DeliveryLog {

    @Id
    @Column(name = "id", length = 36, nullable = false, updatable = false)
    private String id;

    @Column(name = "job_id", length = 36, nullable = false)
    private String jobId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "response_code")
    private Integer responseCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private JobStatus status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "response_body", columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    public DeliveryLog() {
        this.id = UUID.randomUUID().toString();
        this.attemptedAt = Instant.now();
    }

    public DeliveryLog(String jobId, int attemptNumber, Integer responseCode, JobStatus status, String errorMessage, String responseBody) {
        this();
        this.jobId = jobId;
        this.attemptNumber = attemptNumber;
        this.responseCode = responseCode;
        this.status = status;
        this.errorMessage = errorMessage;
        this.responseBody = responseBody;
    }

    @PrePersist
    protected void onCreate() {
        if (this.id == null) {
            this.id = UUID.randomUUID().toString();
        }
        if (this.attemptedAt == null) {
            this.attemptedAt = Instant.now();
        }
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public void setResponseCode(Integer responseCode) {
        this.responseCode = responseCode;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(Instant attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DeliveryLog that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

package com.web3task.webhook.dto;

import com.web3task.webhook.entity.DeliveryLog;
import com.web3task.webhook.entity.JobStatus;

import java.time.Instant;

public class DeliveryLogResponse {

    private String id;
    private String jobId;
    private int attemptNumber;
    private Integer responseCode;
    private JobStatus status;
    private String errorMessage;
    private String responseBody;
    private Instant attemptedAt;

    public DeliveryLogResponse() {
    }

    public static DeliveryLogResponse fromEntity(DeliveryLog log) {
        DeliveryLogResponse res = new DeliveryLogResponse();
        res.id = log.getId();
        res.jobId = log.getJobId();
        res.attemptNumber = log.getAttemptNumber();
        res.responseCode = log.getResponseCode();
        res.status = log.getStatus();
        res.errorMessage = log.getErrorMessage();
        res.responseBody = log.getResponseBody();
        res.attemptedAt = log.getAttemptedAt();
        return res;
    }

    public String getId() {
        return id;
    }

    public String getJobId() {
        return jobId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public Integer getResponseCode() {
        return responseCode;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}

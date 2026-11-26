package com.web3task.webhook.dto;

public class WebhookDispatchResponse {

    private String status;
    private String jobId;

    public WebhookDispatchResponse() {
    }

    public WebhookDispatchResponse(String status, String jobId) {
        this.status = status;
        this.jobId = jobId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }
}

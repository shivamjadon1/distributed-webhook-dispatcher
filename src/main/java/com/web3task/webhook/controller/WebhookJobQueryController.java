package com.web3task.webhook.controller;

import com.web3task.webhook.dto.DeliveryLogResponse;
import com.web3task.webhook.dto.WebhookJobResponse;
import com.web3task.webhook.entity.DeliveryLog;
import com.web3task.webhook.entity.JobStatus;
import com.web3task.webhook.entity.WebhookJob;
import com.web3task.webhook.exception.ResourceNotFoundException;
import com.web3task.webhook.repository.DeliveryLogRepository;
import com.web3task.webhook.repository.WebhookJobRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/webhooks/jobs")
public class WebhookJobQueryController {

    private final WebhookJobRepository webhookJobRepository;
    private final DeliveryLogRepository deliveryLogRepository;

    public WebhookJobQueryController(
            WebhookJobRepository webhookJobRepository,
            DeliveryLogRepository deliveryLogRepository) {
        this.webhookJobRepository = webhookJobRepository;
        this.deliveryLogRepository = deliveryLogRepository;
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<WebhookJobResponse> getJobById(@PathVariable String jobId) {
        WebhookJob job = webhookJobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Webhook job not found with ID: " + jobId));
        return ResponseEntity.ok(WebhookJobResponse.fromEntity(job));
    }

    @GetMapping("/{jobId}/logs")
    public ResponseEntity<List<DeliveryLogResponse>> getJobLogs(@PathVariable String jobId) {
        if (!webhookJobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Webhook job not found with ID: " + jobId);
        }
        List<DeliveryLog> logs = deliveryLogRepository.findByJobIdOrderByAttemptNumberAsc(jobId);
        List<DeliveryLogResponse> dtoList = logs.stream()
                .map(DeliveryLogResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping
    public ResponseEntity<List<WebhookJobResponse>> listRecentJobs() {
        List<WebhookJob> jobs = webhookJobRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        List<WebhookJobResponse> dtoList = jobs.stream()
                .map(WebhookJobResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(dtoList);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getJobStats() {
        List<WebhookJob> jobs = webhookJobRepository.findAll();
        long total = jobs.size();
        long pending = jobs.stream().filter(j -> j.getStatus() == JobStatus.PENDING).count();
        long processing = jobs.stream().filter(j -> j.getStatus() == JobStatus.PROCESSING).count();
        long retrying = jobs.stream().filter(j -> j.getStatus() == JobStatus.RETRYING).count();
        long success = jobs.stream().filter(j -> j.getStatus() == JobStatus.SUCCESS).count();
        long failed = jobs.stream().filter(j -> j.getStatus() == JobStatus.FAILED).count();

        return ResponseEntity.ok(Map.of(
                "total", total,
                "pending", pending,
                "processing", processing,
                "retrying", retrying,
                "success", success,
                "failed", failed
        ));
    }
}

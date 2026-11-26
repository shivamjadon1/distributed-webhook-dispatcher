package com.web3task.webhook.controller;

import com.web3task.webhook.dto.WebhookDispatchRequest;
import com.web3task.webhook.dto.WebhookDispatchResponse;
import com.web3task.webhook.service.WebhookIngestionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookDispatchController {

    private final WebhookIngestionService webhookIngestionService;

    public WebhookDispatchController(WebhookIngestionService webhookIngestionService) {
        this.webhookIngestionService = webhookIngestionService;
    }

    /**
     * Ingestion endpoint for dispatching webhooks asynchronously.
     * Validates input & URL SSRF, creates job in DB, and returns HTTP 202 immediately.
     */
    @PostMapping("/dispatch")
    public ResponseEntity<WebhookDispatchResponse> dispatchWebhook(
            @Valid @RequestBody WebhookDispatchRequest request,
            HttpServletRequest servletRequest) {
        String clientIp = extractClientIp(servletRequest);
        WebhookDispatchResponse response = webhookIngestionService.ingestWebhook(request, clientIp);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isBlank()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

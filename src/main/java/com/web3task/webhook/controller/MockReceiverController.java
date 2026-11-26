package com.web3task.webhook.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Built-in mock receiver endpoints for testing webhook delivery.
 * Allows simulating 200 OK, 500 server error, 429 rate limiting, and 400 client error.
 */
@RestController
@RequestMapping("/api/mock")
public class MockReceiverController {

    private static final Logger log = LoggerFactory.getLogger(MockReceiverController.class);
    private final AtomicInteger requestCounter = new AtomicInteger(0);

    @PostMapping("/receiver")
    public ResponseEntity<Map<String, Object>> receiveWebhook(@RequestBody(required = false) String payload) {
        int count = requestCounter.incrementAndGet();
        log.info("MockReceiver: Received webhook #{} successfully. Payload: {}", count, payload);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Webhook received successfully",
                "requestNumber", count,
                "receivedAt", Instant.now().toString()
        ));
    }

    @PostMapping("/fail")
    public ResponseEntity<Map<String, Object>> simulate500Error(@RequestBody(required = false) String payload) {
        log.warn("MockReceiver: Simulating 500 Internal Server Error");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "status", "error",
                "message", "Downstream internal server error (simulated 500)",
                "timestamp", Instant.now().toString()
        ));
    }

    @PostMapping("/ratelimit")
    public ResponseEntity<Map<String, Object>> simulate429RateLimit(@RequestBody(required = false) String payload) {
        log.warn("MockReceiver: Simulating 429 Rate Limit with Retry-After: 3");
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", "3");
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(headers)
                .body(Map.of(
                        "status", "rate_limited",
                        "message", "Too many requests. Please retry in 3 seconds.",
                        "retryAfterSeconds", 3
                ));
    }

    @PostMapping("/client-error")
    public ResponseEntity<Map<String, Object>> simulate400Error(@RequestBody(required = false) String payload) {
        log.warn("MockReceiver: Simulating 400 Bad Request");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "status", "bad_request",
                "message", "Invalid webhook payload (simulated 400)"
        ));
    }
}

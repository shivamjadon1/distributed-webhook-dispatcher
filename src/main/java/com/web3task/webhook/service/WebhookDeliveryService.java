package com.web3task.webhook.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final int MAX_RESPONSE_BODY_LENGTH = 2048;

    private final WebClient webClient;

    public WebhookDeliveryService(WebClient webClient) {
        this.webClient = webClient;
    }

    public record DeliveryResult(
            boolean isSuccess,
            boolean isRetryable,
            Integer statusCode,
            String responseBody,
            String errorMessage,
            Long retryAfterSeconds
    ) {}

    /**
     * Executes HTTP POST delivery to the destination URL.
     * Captures status code, response body, Retry-After header, or connection/timeout exceptions.
     */
    public DeliveryResult sendWebhook(String destinationUrl, String payloadJson) {
        try {
            return webClient.post()
                    .uri(destinationUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payloadJson)
                    .exchangeToMono(clientResponse -> {
                        int statusCode = clientResponse.statusCode().value();
                        String retryAfterHeader = clientResponse.headers().asHttpHeaders().getFirst(HttpHeaders.RETRY_AFTER);
                        Long retryAfterSeconds = parseRetryAfter(retryAfterHeader);

                        return clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> {
                                    String sanitizedBody = sanitizeBody(body);
                                    if (statusCode >= 200 && statusCode < 300) {
                                        return new DeliveryResult(true, false, statusCode, sanitizedBody, null, null);
                                    } else if (statusCode == 429) {
                                        return new DeliveryResult(false, true, statusCode, sanitizedBody,
                                                "Downstream rate limit (429 Too Many Requests)", retryAfterSeconds);
                                    } else if (statusCode >= 500) {
                                        return new DeliveryResult(false, true, statusCode, sanitizedBody,
                                                "Downstream server error (" + statusCode + ")", null);
                                    } else {
                                        // Permanent 4xx errors (e.g. 400, 401, 403, 404, 422) - NOT retryable
                                        return new DeliveryResult(false, false, statusCode, sanitizedBody,
                                                "Downstream client error (" + statusCode + ")", null);
                                    }
                                });
                    })
                    .onErrorResume(throwable -> {
                        String errMsg;
                        if (throwable instanceof WebClientRequestException wcre) {
                            errMsg = "Network/Connection error: " + wcre.getMessage();
                        } else {
                            errMsg = "Dispatch error: " + throwable.getMessage();
                        }
                        log.warn("Error delivering webhook to {}: {}", destinationUrl, errMsg);
                        return Mono.just(new DeliveryResult(false, true, null, null, errMsg, null));
                    })
                    .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.error("Unexpected exception delivering webhook to {}", destinationUrl, e);
            return new DeliveryResult(false, true, null, null, "Delivery failure: " + e.getMessage(), null);
        }
    }

    private String sanitizeBody(String body) {
        if (body == null) return null;
        if (body.length() > MAX_RESPONSE_BODY_LENGTH) {
            return body.substring(0, MAX_RESPONSE_BODY_LENGTH) + "... [truncated]";
        }
        return body;
    }

    private Long parseRetryAfter(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        try {
            // Attempt integer seconds parsing
            return Long.parseLong(headerValue.trim());
        } catch (NumberFormatException ignored) {
            // Attempt HTTP-Date parsing (RFC 1123)
            try {
                Instant retryDate = Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(headerValue.trim()));
                long diff = Duration.between(Instant.now(), retryDate).toSeconds();
                return Math.max(diff, 1);
            } catch (Exception e) {
                log.debug("Unparseable Retry-After header: {}", headerValue);
                return null;
            }
        }
    }
}

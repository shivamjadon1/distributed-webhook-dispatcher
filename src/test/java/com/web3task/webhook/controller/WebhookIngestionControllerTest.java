package com.web3task.webhook.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web3task.webhook.dto.WebhookDispatchRequest;
import com.web3task.webhook.dto.WebhookDispatchResponse;
import com.web3task.webhook.exception.GlobalExceptionHandler;
import com.web3task.webhook.exception.RateLimitExceededException;
import com.web3task.webhook.exception.SSRFSecurityException;
import com.web3task.webhook.service.WebhookIngestionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(WebhookDispatchController.class)
@Import(GlobalExceptionHandler.class)
class WebhookIngestionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WebhookIngestionService ingestionService;

    @Test
    @DisplayName("Valid ingestion returns HTTP 202 Accepted immediately with status queued")
    void testSuccessfulIngestionReturns202() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("event", "user.created");
        payload.put("id", "usr_100");

        WebhookDispatchRequest req = new WebhookDispatchRequest(
                "https://api.external.com/webhooks",
                "evt_987654",
                payload
        );

        when(ingestionService.ingestWebhook(any(), anyString()))
                .thenReturn(new WebhookDispatchResponse("queued", "job-uuid-1234"));

        mockMvc.perform(post("/api/webhooks/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.jobId").value("job-uuid-1234"));
    }

    @Test
    @DisplayName("Missing destinationUrl or eventId triggers HTTP 400 Bad Request")
    void testInvalidPayloadReturns400() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();

        // destinationUrl is blank
        WebhookDispatchRequest invalidReq = new WebhookDispatchRequest(
                "",
                "evt_111",
                payload
        );

        mockMvc.perform(post("/api/webhooks/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.validationErrors.destinationUrl").exists());
    }

    @Test
    @DisplayName("SSRF security exception translates to HTTP 400")
    void testSSRFSecurityViolationReturns400() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("data", "test");

        WebhookDispatchRequest req = new WebhookDispatchRequest(
                "http://169.254.169.254/latest",
                "evt_ssrf",
                payload
        );

        when(ingestionService.ingestWebhook(any(), anyString()))
                .thenThrow(new SSRFSecurityException("Access to cloud metadata blocked"));

        mockMvc.perform(post("/api/webhooks/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SSRF Protection Error"))
                .andExpect(jsonPath("$.message").value("Access to cloud metadata blocked"));
    }

    @Test
    @DisplayName("Ingestion rate limit exceeded triggers HTTP 429 with Retry-After header")
    void testIngestionRateLimitReturns429() throws Exception {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("data", "test");

        WebhookDispatchRequest req = new WebhookDispatchRequest(
                "https://api.external.com/webhooks",
                "evt_rate",
                payload
        );

        when(ingestionService.ingestWebhook(any(), anyString()))
                .thenThrow(new RateLimitExceededException("Ingestion limit exceeded", 2));

        mockMvc.perform(post("/api/webhooks/dispatch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "2"))
                .andExpect(jsonPath("$.status").value(429));
    }
}

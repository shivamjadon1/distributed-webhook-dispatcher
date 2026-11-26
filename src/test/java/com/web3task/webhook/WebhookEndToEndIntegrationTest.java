package com.web3task.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.web3task.webhook.dto.DeliveryLogResponse;
import com.web3task.webhook.dto.WebhookDispatchRequest;
import com.web3task.webhook.dto.WebhookDispatchResponse;
import com.web3task.webhook.dto.WebhookJobResponse;
import com.web3task.webhook.entity.JobStatus;
import com.web3task.webhook.worker.WebhookDispatchWorker;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class WebhookEndToEndIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WebhookDispatchWorker webhookDispatchWorker;

    private MockWebServer mockWebServer;
    private String mockServerUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        mockServerUrl = mockWebServer.url("/receive-hook").toString();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("End-to-End: Ingestion -> Immediate 202 -> Dispatch -> DB Audit -> Query APIs")
    void testFullWebhookLifecycle() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\": \"received\"}"));

        String baseUrl = "http://localhost:" + port;

        // 1. Post webhook request
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("event", "payment.succeeded");
        payload.put("amount", 2500);
        payload.put("currency", "USD");

        WebhookDispatchRequest request = new WebhookDispatchRequest(
                mockServerUrl,
                "evt_e2e_101",
                payload
        );

        ResponseEntity<WebhookDispatchResponse> postResponse = restTemplate.postForEntity(
                baseUrl + "/api/webhooks/dispatch",
                request,
                WebhookDispatchResponse.class
        );

        assertEquals(HttpStatus.ACCEPTED, postResponse.getStatusCode());
        assertNotNull(postResponse.getBody());
        assertEquals("queued", postResponse.getBody().getStatus());
        String jobId = postResponse.getBody().getJobId();
        assertNotNull(jobId);

        // 2. Process the job via worker
        webhookDispatchWorker.processJob(jobId);

        // 3. Query Job status API
        ResponseEntity<WebhookJobResponse> jobResponse = restTemplate.getForEntity(
                baseUrl + "/api/webhooks/jobs/" + jobId,
                WebhookJobResponse.class
        );
        assertEquals(HttpStatus.OK, jobResponse.getStatusCode());
        assertNotNull(jobResponse.getBody());
        assertEquals(JobStatus.SUCCESS, jobResponse.getBody().getStatus());
        assertEquals(1, jobResponse.getBody().getAttempts());

        // 4. Query Job Logs API
        ResponseEntity<List<DeliveryLogResponse>> logsResponse = restTemplate.exchange(
                baseUrl + "/api/webhooks/jobs/" + jobId + "/logs",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, logsResponse.getStatusCode());
        List<DeliveryLogResponse> logs = logsResponse.getBody();
        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals(200, logs.get(0).getResponseCode());
        assertEquals(JobStatus.SUCCESS, logs.get(0).getStatus());
    }

    @Test
    @DisplayName("End-to-End: Network connection failure triggers retryable error")
    void testNetworkFailureTriggersRetry() {
        // Target an unroutable port on mockServer to trigger immediate connection failure
        String invalidDestination = "http://127.0.0.1:1";

        String baseUrl = "http://localhost:" + port;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("test", "conn_error");

        WebhookDispatchRequest request = new WebhookDispatchRequest(
                invalidDestination,
                "evt_conn_fail",
                payload
        );

        ResponseEntity<WebhookDispatchResponse> postResponse = restTemplate.postForEntity(
                baseUrl + "/api/webhooks/dispatch",
                request,
                WebhookDispatchResponse.class
        );

        assertEquals(HttpStatus.ACCEPTED, postResponse.getStatusCode());
        String jobId = postResponse.getBody().getJobId();

        // Process job
        webhookDispatchWorker.processJob(jobId);

        // Verify status is RETRYING
        ResponseEntity<WebhookJobResponse> jobResponse = restTemplate.getForEntity(
                baseUrl + "/api/webhooks/jobs/" + jobId,
                WebhookJobResponse.class
        );
        assertEquals(HttpStatus.OK, jobResponse.getStatusCode());
        assertEquals(JobStatus.RETRYING, jobResponse.getBody().getStatus());
        assertEquals(1, jobResponse.getBody().getAttempts());
    }
}

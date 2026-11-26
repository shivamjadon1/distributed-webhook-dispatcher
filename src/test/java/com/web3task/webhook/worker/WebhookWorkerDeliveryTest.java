package com.web3task.webhook.worker;

import com.web3task.webhook.entity.DeliveryLog;
import com.web3task.webhook.entity.JobStatus;
import com.web3task.webhook.entity.WebhookJob;
import com.web3task.webhook.repository.DeliveryLogRepository;
import com.web3task.webhook.repository.WebhookJobRepository;
import com.web3task.webhook.service.WebhookJobService;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class WebhookWorkerDeliveryTest {

    @Autowired
    private WebhookJobRepository webhookJobRepository;

    @Autowired
    private DeliveryLogRepository deliveryLogRepository;

    @Autowired
    private WebhookJobService webhookJobService;

    @Autowired
    private WebhookDispatchWorker webhookDispatchWorker;

    private MockWebServer mockWebServer;
    private String destinationUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        destinationUrl = mockWebServer.url("/webhook-receiver").toString();

        deliveryLogRepository.deleteAll();
        webhookJobRepository.deleteAll();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
        deliveryLogRepository.deleteAll();
        webhookJobRepository.deleteAll();
    }

    @Test
    @DisplayName("Downstream HTTP 200 marks job SUCCESS and logs delivery")
    void testSuccessfulDelivery() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"received\": true}"));

        WebhookJob job = new WebhookJob("evt_success", destinationUrl, "{\"msg\": \"hello\"}");
        job = webhookJobRepository.save(job);

        webhookDispatchWorker.processJob(job.getId());

        WebhookJob updated = webhookJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.SUCCESS, updated.getStatus());
        assertEquals(1, updated.getAttempts());

        List<DeliveryLog> logs = deliveryLogRepository.findByJobIdOrderByAttemptNumberAsc(job.getId());
        assertEquals(1, logs.size());
        assertEquals(200, logs.get(0).getResponseCode());
        assertEquals(JobStatus.SUCCESS, logs.get(0).getStatus());
    }

    @Test
    @DisplayName("Downstream HTTP 500 triggers retry and schedules backoff")
    void testHttp500RetryHandling() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        WebhookJob job = new WebhookJob("evt_500", destinationUrl, "{\"msg\": \"retry\"}");
        job = webhookJobRepository.save(job);

        webhookDispatchWorker.processJob(job.getId());

        WebhookJob updated = webhookJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.RETRYING, updated.getStatus());
        assertEquals(1, updated.getAttempts());
        assertNotNull(updated.getNextRetryAt());
        assertTrue(updated.getNextRetryAt().isAfter(Instant.now()));

        List<DeliveryLog> logs = deliveryLogRepository.findByJobIdOrderByAttemptNumberAsc(job.getId());
        assertEquals(1, logs.size());
        assertEquals(500, logs.get(0).getResponseCode());
        assertEquals(JobStatus.RETRYING, logs.get(0).getStatus());
    }

    @Test
    @DisplayName("Downstream HTTP 429 parses Retry-After header")
    void testHttp429RetryAfterHeader() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "6")
                .setBody("Too Many Requests"));

        WebhookJob job = new WebhookJob("evt_429", destinationUrl, "{\"msg\": \"rate_limited\"}");
        job = webhookJobRepository.save(job);

        webhookDispatchWorker.processJob(job.getId());

        WebhookJob updated = webhookJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.RETRYING, updated.getStatus());
        assertEquals(1, updated.getAttempts());

        // nextRetryAt should be at least ~5 seconds from now
        long diffSeconds = updated.getNextRetryAt().getEpochSecond() - Instant.now().getEpochSecond();
        assertTrue(diffSeconds >= 4 && diffSeconds <= 7, "Retry-After of 6s should be respected");
    }

    @Test
    @DisplayName("Permanent 4xx errors (400, 404) do not retry and immediately mark FAILED")
    void testPermanentClientErrorNoRetry() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(404)
                .setBody("Endpoint Not Found"));

        WebhookJob job = new WebhookJob("evt_404", destinationUrl, "{\"msg\": \"bad\"}");
        job = webhookJobRepository.save(job);

        webhookDispatchWorker.processJob(job.getId());

        WebhookJob updated = webhookJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.FAILED, updated.getStatus(), "Permanent 404 must not be retried");
        assertEquals(1, updated.getAttempts());
        assertNull(updated.getNextRetryAt());

        List<DeliveryLog> logs = deliveryLogRepository.findByJobIdOrderByAttemptNumberAsc(job.getId());
        assertEquals(1, logs.size());
        assertEquals(404, logs.get(0).getResponseCode());
        assertEquals(JobStatus.FAILED, logs.get(0).getStatus());
    }

    @Test
    @DisplayName("Exponential backoff schedule: 2s -> 4s -> 8s")
    void testExponentialBackoffSchedule() {
        // Attempt 1 -> 2000 ms
        assertEquals(2000L, webhookDispatchWorker.calculateBackoffMs(1, null));
        // Attempt 2 -> 4000 ms
        assertEquals(4000L, webhookDispatchWorker.calculateBackoffMs(2, null));
        // Attempt 3 -> 8000 ms
        assertEquals(8000L, webhookDispatchWorker.calculateBackoffMs(3, null));
    }

    @Test
    @DisplayName("Retry exhaustion marks job as FAILED after max retries")
    void testRetryExhaustion() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        WebhookJob job = new WebhookJob("evt_exhaust", destinationUrl, "{\"msg\": \"exhaust\"}");
        job.setAttempts(3); // Already attempted 3 times (initial + 2 retries)
        job.setStatus(JobStatus.RETRYING);
        job = webhookJobRepository.save(job);

        webhookDispatchWorker.processJob(job.getId());

        WebhookJob updated = webhookJobRepository.findById(job.getId()).orElseThrow();
        assertEquals(JobStatus.FAILED, updated.getStatus());
        assertEquals(4, updated.getAttempts());
        assertNull(updated.getNextRetryAt());
    }

    @Test
    @DisplayName("Concurrent job claiming ensures only one worker processes the job")
    void testConcurrentJobClaiming() throws Exception {
        WebhookJob job = new WebhookJob("evt_concurrency", destinationUrl, "{\"msg\": \"race\"}");
        job = webhookJobRepository.save(job);
        String jobId = job.getId();

        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CyclicBarrier barrier = new CyclicBarrier(threads);
        List<Future<Boolean>> futures = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                barrier.await();
                return webhookJobService.claimJob(jobId);
            }));
        }

        int successfulClaims = 0;
        for (Future<Boolean> f : futures) {
            if (f.get(5, TimeUnit.SECONDS)) {
                successfulClaims++;
            }
        }
        executor.shutdown();

        assertEquals(1, successfulClaims, "Exactly one worker should successfully claim the job");
    }
}

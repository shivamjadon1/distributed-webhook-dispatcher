# Distributed Webhook Dispatcher Engine

A production-ready, distributed, resilient webhook delivery engine built with **Java 17**, **Spring Boot 3.3.5**, **PostgreSQL**, **Spring Data JPA / Hibernate**, **Spring WebClient (Reactive Netty)**, **Flyway**, and **Bucket4j**.

---

## 1. Architectural Overview & Design Philosophy

The engine decouples webhook ingestion from downstream HTTP delivery via a **database-backed job queue**.

```
[ Client / Producer ]
        │  POST /api/webhooks/dispatch
        ▼
┌────────────────────────────────────────────────────────┐
│ 1. Ingestion Layer                                     │
│    ├── Bucket4j Ingestion Rate Limiter (HTTP 429)      │
│    ├── SafeUrlValidator (SSRF Protection & DNS Check)  │
│    └── Persist WebhookJob (Status: PENDING) in Postgres│
└────────────────────────────────────────────────────────┘
        │
        │ HTTP 202 Accepted { "status": "queued", "jobId": "..." } (Immediate)
        ▼
┌────────────────────────────────────────────────────────┐
│ 2. PostgreSQL Queue (Survives Restarts)               │
│    ├── webhook_jobs (Status: PENDING | RETRYING)       │
│    └── delivery_logs (Auditable Attempt History)      │
└────────────────────────────────────────────────────────┘
        ▲
        │ Atomic Polling & Claiming (FOR UPDATE SKIP LOCKED / Atomic State)
┌───────┴────────────────────────────────────────────────┐
│ 3. Asynchronous Worker Fleet (Thread Pool)             │
│    ├── Phase 1: Atomically claim job (PROCESSING)      │
│    ├── Phase 2: Per-domain Downstream Rate Limiter     │
│    ├── Phase 3: WebClient Dispatch (Non-blocking Netty)│
│    │            - Connection & Read Timeouts           │
│    │            - DNS Rebinding Defense Resolver       │
│    │            - No DB transaction held during HTTP!  │
│    └── Phase 4: Retry / Outcome Resolution             │
│                 - 200 OK  ──► SUCCESS                  │
│                 - 4xx     ──► FAILED (No Retry)        │
│                 - 5xx/429 ──► RETRYING (Exp Backoff)   │
│                 - Max Exh ──► FAILED                   │
└────────────────────────────────────────────────────────┘
```

### Why a Database-Backed Queue?
- **Zero Data Loss Across Restarts**: If the service restarts or crashes mid-flight, jobs remain in PostgreSQL with `next_retry_at` timestamps intact and are picked up immediately on boot.
- **Strict Transaction Boundaries**: Database transactions are never held open while waiting for external networks. Workers claim a batch in a microsecond transaction, release connection pool locks, make the network call asynchronously, and write delivery audit logs in a new isolated transaction.
- **Atomic Concurrency**: Multiple application instances safely compete for pending jobs without race conditions or double-deliveries.

---

## 2. Technology Stack

- **Language**: Java 17 (tested & validated on OpenJDK / Oracle JDK 17 & 22)
- **Framework**: Spring Boot 3.3.5
- **Persistence**: Spring Data JPA, Hibernate ORM 6.5
- **Database**: PostgreSQL 17 (Production/Dev), H2 PostgreSQL Mode (Automated Test Suite)
- **Database Migrations**: Flyway Core & Flyway Database PostgreSQL
- **HTTP Client**: Spring Reactive WebClient with Reactor Netty
- **Rate Limiting**: Bucket4j Token Bucket (`com.bucket4j:bucket4j-core:8.10.1`)
- **Testing**: JUnit 5, Mockito, Spring Boot Test, OkHttp MockWebServer, Awaitility
- **Containerization & Deployment**: Docker (Multi-stage build), Render, Railway

---

## 3. Security & SSRF Protection

Server-Side Request Forgery (SSRF) and DNS rebinding attacks are mitigated through layered security defenses:

1. **Protocol Whitelisting**: Strictly restricts protocols to `https://` (and `http://` in explicit dev/testing profile). Protocols such as `file://`, `ftp://`, `gopher://`, `jar://` are blocked.
2. **Credential Stripping**: URL embedded user info (`http://user:pass@host`) is blocked.
3. **Blacklisted Cloud Metadata & Loopback**:
   - `169.254.169.254` (AWS, GCP, Azure, OpenStack instance metadata)
   - `metadata.google.internal`, `instance-data`
   - `127.0.0.1` / `localhost` / `::1`
   - Carrier-Grade NAT (`100.64.0.0/10`)
   - RFC 1918 Private Ranges (`10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16`)
   - IPv6 Link-Local (`fe80::/10`) and Unique-Local (`fc00::/7`)
   - IPv4-mapped IPv6 addresses (`::ffff:127.0.0.1`, `::ffff:169.254.169.254`)
4. **DNS Rebinding Defense**:
   - Hostname resolution is verified at ingestion.
   - The Netty HTTP connector utilizes a custom `DnsRebindingPreventingResolver` that re-checks the resolved IP address immediately prior to socket connection establishment. If a DNS record changes between ingestion and socket connect to target a local/private IP, the connection is aborted.
5. **Redirect Hijack Prevention**: Automated HTTP redirect following is disabled (`followRedirect(false)`) to prevent target servers from returning 302 redirects to internal microservices.

---

## 4. Rate Limiting Architecture

The engine implements two independent token-bucket rate limiters via **Bucket4j**:

### Ingestion Rate Limiter (Protects Dispatcher)
- Tracks incoming ingestion requests per client IP (using `X-Forwarded-For` or client remote IP).
- Default: 100 requests per second burst capacity, refilling 100 tokens every second.
- When capacity is exhausted, the API immediately responds with **HTTP 429 Too Many Requests** along with a `Retry-After: 1` header.

### Downstream Delivery Rate Limiter (Protects Target Endpoints)
- Tracks outgoing delivery attempts **per destination domain** (e.g. `api.merchant.com`).
- Default: 10 requests per second per domain.
- If a burst of webhooks targets the same destination domain, the worker defers the job with a 1-second reschedule without marking it as a failed delivery attempt or consuming retries.

### Multi-Instance Scaling with Redis
In a multi-node cluster, local `ConcurrentHashMap` caches can be swapped for Redis by configuring Bucket4j's Lettuce backend:
```java
// Redis Lettuce proxy configuration:
RedisClient redisClient = RedisClient.create("redis://localhost:6379");
StatefulRedisConnection<String, byte[]> connection = redisClient.connect(RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));
ProxyManager<String> proxyManager = LettuceBasedProxyManager.builderFor(connection).build();
```

---

## 5. Retry Mechanism & Delivery Lifecycle

### Job Statuses
- `PENDING`: Newly ingested job awaiting first delivery attempt.
- `PROCESSING`: Job currently claimed by a worker thread.
- `RETRYING`: Transient failure encountered; waiting for `next_retry_at`.
- `SUCCESS`: Webhook delivered with HTTP 2xx response.
- `FAILED`: Permanently aborted due to client error (4xx) or exhausted retries.

### Retry Schedule & Exponential Backoff
- **Total attempts**: Initial delivery attempt + up to 3 retries (Maximum 4 total attempts).
- **Retry 1**: 2 seconds backoff.
- **Retry 2**: 4 seconds backoff.
- **Retry 3**: 8 seconds backoff.
- **After Retry 3 fails**: Mark as `FAILED` (retries exhausted).

### Retry Trigger Conditions
- Downstream **HTTP 5xx** (500, 502, 503, 504).
- Downstream **HTTP 429 Too Many Requests**.
- Network connection failures (e.g. host unreachable, connection refused).
- Request timeouts (connect timeout 5000ms, response timeout 5000ms).

### Non-Retryable Conditions
- Permanent downstream **HTTP 4xx** (400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 422 Unprocessable Entity). Marked `FAILED` immediately on attempt 1.

### `Retry-After` Header Handling
When a target responds with HTTP 429, the engine inspects the `Retry-After` header (both delta-seconds and RFC 1123 HTTP-Date). If the downstream requested delay exceeds the default exponential backoff, the engine honors the larger delay to respect destination backpressure.

---

## 6. Database Schema & Flyway Migrations

Migrations are located in `src/main/resources/db/migration/V1__init_schema.sql`.

```sql
CREATE TABLE webhook_jobs (
    id VARCHAR(36) PRIMARY KEY,
    event_id VARCHAR(255) NOT NULL,
    destination_url TEXT NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempts INT NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_jobs_queue 
    ON webhook_jobs (status, next_retry_at, created_at);

CREATE INDEX idx_webhook_jobs_event_id 
    ON webhook_jobs (event_id);

CREATE TABLE delivery_logs (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL,
    attempt_number INT NOT NULL,
    response_code INT,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    response_body TEXT,
    attempted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_delivery_logs_job FOREIGN KEY (job_id) REFERENCES webhook_jobs (id) ON DELETE CASCADE
);

CREATE INDEX idx_delivery_logs_job_id 
    ON delivery_logs (job_id, attempt_number);

CREATE INDEX idx_delivery_logs_attempted_at 
    ON delivery_logs (attempted_at);
```

---

## 7. API Reference

### 1. Ingest Webhook Dispatch
- **Endpoint**: `POST /api/webhooks/dispatch`
- **Status Code**: `202 Accepted`

#### Request JSON
```json
{
  "destinationUrl": "https://httpbin.org/post",
  "eventId": "evt_998877",
  "payload": {
    "event": "user.signup",
    "userId": "usr_12345",
    "tier": "enterprise"
  }
}
```

#### Response JSON
```json
{
  "status": "queued",
  "jobId": "42b7326d-ed3c-4baf-8399-a977497876c5"
}
```

#### Sample cURL
```bash
curl -X POST http://localhost:8080/api/webhooks/dispatch \
  -H "Content-Type: application/json" \
  -d '{
    "destinationUrl": "https://httpbin.org/post",
    "eventId": "evt_sample_001",
    "payload": {
      "event": "order.completed",
      "orderId": "ord_999",
      "amount": 149.99
    }
  }'
```

---

### 2. Query Job Status
- **Endpoint**: `GET /api/webhooks/jobs/{jobId}`
- **Status Code**: `200 OK`

#### Response JSON
```json
{
  "id": "42b7326d-ed3c-4baf-8399-a977497876c5",
  "eventId": "evt_sample_001",
  "destinationUrl": "https://httpbin.org/post",
  "payload": "{\"amount\":149.99,\"event\":\"order.completed\",\"orderId\":\"ord_999\"}",
  "status": "SUCCESS",
  "attempts": 1,
  "nextRetryAt": null,
  "createdAt": "2026-11-26T06:50:47.992998Z",
  "updatedAt": "2026-11-26T06:50:49.907284Z"
}
```

---

### 3. Query Delivery Logs for Job
- **Endpoint**: `GET /api/webhooks/jobs/{jobId}/logs`
- **Status Code**: `200 OK`

#### Response JSON
```json
[
  {
    "id": "a2ae214d-d29e-4d1b-b4e8-b5ff790aa511",
    "jobId": "42b7326d-ed3c-4baf-8399-a977497876c5",
    "attemptNumber": 1,
    "responseCode": 200,
    "status": "SUCCESS",
    "errorMessage": null,
    "responseBody": "{\n  \"data\": \"{\\\"amount\\\":149.99...}\"\n}",
    "attemptedAt": "2026-11-26T06:50:49.902852Z"
  }
]
```

---

### 4. Health Check Endpoints
- `GET /api/health` -> Custom JSON health check
- `GET /actuator/health` -> Spring Boot Actuator with PostgreSQL & disk space metrics

---

## 8. Local Setup & Running Instructions

### Prerequisites
- Java 17+
- Maven 3.9+ (or use included `./mvnw`)
- PostgreSQL 14+

### Step 1: Create Database
```sql
CREATE DATABASE webhook_db;
```

### Step 2: Configure Environment Variables (or use defaults)
```bash
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=webhook_db
export DB_USER=postgres
export DB_PASSWORD=your_password
```

### Step 3: Build & Test
```bash
./mvnw clean test
```

### Step 4: Run Application
```bash
./mvnw spring-boot:run
```

---

## 9. Automated Testing Coverage

The test suite consists of **26 comprehensive automated tests** validating all required scenarios:

| Category | Test Description | Result |
|---|---|---|
| **Ingestion** | Successful ingestion returns HTTP 202 Accepted with status `queued` | PASS |
| **Validation** | Blank `destinationUrl`, `eventId`, or missing `payload` returns HTTP 400 | PASS |
| **Ingestion Rate Limit** | Excess requests return HTTP 429 with `Retry-After` header | PASS |
| **SSRF Defense** | Localhost, private RFC 1918, cloud metadata IPs/hosts blocked with HTTP 400 | PASS |
| **Downstream Rate Limit**| Token bucket isolates tokens per domain | PASS |
| **Delivery Success** | HTTP 200 response updates status to `SUCCESS` and records log | PASS |
| **HTTP 500 Handling** | Downstream 500 marks status `RETRYING` and increments attempt count | PASS |
| **HTTP 429 & Retry-After**| Downstream 429 respects `Retry-After` header backoff | PASS |
| **Permanent 4xx Error** | Downstream 400/404 immediately marked `FAILED` without retrying | PASS |
| **Exponential Backoff** | Backoff schedules validated at 2s, 4s, 8s intervals | PASS |
| **Retry Exhaustion** | 4th attempt failure marks status `FAILED` permanently | PASS |
| **Concurrency Safety** | Multi-threaded race condition test verifies atomic job claiming | PASS |
| **Network Failure** | Connection refused/timeout correctly captured and marked for retry | PASS |

---

## 10. Deployment (Render / Railway / Docker)

### Docker Deployment
```bash
docker build -t distributed-webhook-dispatcher .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db.host:5432/webhook_db \
  -e SPRING_DATASOURCE_USERNAME=user \
  -e SPRING_DATASOURCE_PASSWORD=pass \
  distributed-webhook-dispatcher
```

### Render Deployment
This repository contains `render.yaml` defining a Render Blueprint:
1. Connect your GitHub repository to Render.
2. Select **New > Blueprint**.
3. Render automatically provisions a managed PostgreSQL database and the Web Service container with port 8080 and `/actuator/health` health checks.
4. The application automatically detects and parses Render's `DATABASE_URL` format.

---

## 11. Known Limitations & Future Roadmap

- **Dead Letter Queue (DLQ)**: Add an event notification (e.g. AWS SQS / Kafka) when a webhook enters `FAILED` status after retry exhaustion.
- **HMAC Signatures**: Add configurable cryptographic signatures (`X-Hub-Signature-256`) with per-destination shared secrets.
- **Mutual TLS (mTLS)**: Support client certificates for regulated downstream endpoints.

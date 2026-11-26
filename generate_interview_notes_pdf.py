import os
import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.units import inch
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether, HRFlowable
)
from reportlab.pdfgen import canvas

class NumberedCanvas(canvas.Canvas):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_header_footer(num_pages)
            super().showPage()
        super().save()

    def draw_header_footer(self, page_count):
        self.saveState()
        self.setFont("Helvetica-Bold", 8)
        self.setFillColor(colors.HexColor("#64748B"))
        
        # Header (pages > 1)
        if self._pageNumber > 1:
            self.drawString(54, 750, "DISTRIBUTED WEBHOOK DISPATCHER ENGINE")
            self.setFont("Helvetica", 8)
            self.drawRightString(558, 750, "Technical Interview Preparation Guide")
            self.setStrokeColor(colors.HexColor("#CBD5E1"))
            self.setLineWidth(0.5)
            self.line(54, 744, 558, 744)
        
        # Footer (all pages)
        self.setStrokeColor(colors.HexColor("#CBD5E1"))
        self.setLineWidth(0.5)
        self.line(54, 45, 558, 45)
        
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#64748B"))
        self.drawString(54, 32, "Candidate: Shivam Jadon  |  Tech Stack: Java 17, Spring Boot, Netty, Postgres, Bucket4j")
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 32, page_str)
        self.restoreState()

def build_pdf(filename):
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=54
    )

    styles = getSampleStyleSheet()
    
    # Custom styles
    c_primary = colors.HexColor("#0F172A")    # Deep slate
    c_blue = colors.HexColor("#1D4ED8")       # Royal blue
    c_accent = colors.HexColor("#0284C7")     # Sky blue
    c_dark = colors.HexColor("#334155")       # Charcoal
    c_bg_callout = colors.HexColor("#F8FAFC") # Light gray-blue
    c_border = colors.HexColor("#E2E8F0")

    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=24,
        leading=28,
        textColor=c_primary,
        spaceAfter=6
    )
    
    subtitle_style = ParagraphStyle(
        'DocSubTitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=12,
        leading=16,
        textColor=c_blue,
        spaceAfter=15
    )
    
    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=15,
        leading=19,
        textColor=c_blue,
        spaceBefore=14,
        spaceAfter=8,
        keepWithNext=True
    )

    h2_style = ParagraphStyle(
        'Heading2_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=11,
        leading=15,
        textColor=c_primary,
        spaceBefore=10,
        spaceAfter=4,
        keepWithNext=True
    )

    body_style = ParagraphStyle(
        'Body_Custom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9,
        leading=13.5,
        textColor=c_dark,
        spaceAfter=6
    )

    body_bold = ParagraphStyle(
        'Body_Bold_Custom',
        parent=body_style,
        fontName='Helvetica-Bold'
    )

    callout_style = ParagraphStyle(
        'Callout_Text',
        parent=styles['Normal'],
        fontName='Helvetica-Oblique',
        fontSize=8.5,
        leading=13,
        textColor=colors.HexColor("#1E293B")
    )

    q_style = ParagraphStyle(
        'Question_Style',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=10,
        leading=14,
        textColor=colors.HexColor("#0F172A"),
        spaceBefore=8,
        spaceAfter=3,
        keepWithNext=True
    )

    a_style = ParagraphStyle(
        'Answer_Style',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=8.5,
        leading=13,
        textColor=colors.HexColor("#334155"),
        spaceAfter=8
    )

    code_style = ParagraphStyle(
        'Code_Style',
        parent=styles['Normal'],
        fontName='Courier',
        fontSize=8,
        leading=11,
        textColor=colors.HexColor("#0F172A")
    )

    story = []

    # Title & Metadata
    story.append(Paragraph("Distributed Webhook Dispatcher Engine", title_style))
    story.append(Paragraph("Comprehensive Technical Interview Preparation Notes & System Design Guide", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=c_blue, spaceBefore=0, spaceAfter=10))

    meta_data = [
        [Paragraph("<b>Candidate:</b> Shivam Jadon", body_style), 
         Paragraph("<b>Tech Stack:</b> Java 17, Spring Boot 3.3.5, PostgreSQL, Netty WebClient", body_style)],
        [Paragraph("<b>Live Endpoint:</b> <font color='#1D4ED8'>https://distributed-webhook-dispatcher-production-8cecda62.up.railway.app</font>", body_style),
         Paragraph("<b>GitHub:</b> <font color='#1D4ED8'>github.com/shivamjadon1/distributed-webhook-dispatcher</font>", body_style)]
    ]
    meta_table = Table(meta_data, colWidths=[240, 264])
    meta_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), c_bg_callout),
        ('BOX', (0,0), (-1,-1), 1, c_border),
        ('PADDING', (0,0), (-1,-1), 6),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 10))

    # SECTION 1: Elevator Pitch
    story.append(Paragraph("1. The 90-Second Interview Elevator Pitch", h1_style))
    pitch_text = """
    <b>How to answer:</b> <i>\"Can you introduce your project and what core problem it solves?\"</i><br/><br/>
    \"I engineered a production-grade <b>Distributed Webhook Dispatcher Engine</b> designed to broadcast events from internal services to third-party endpoints reliably and safely without blocking caller APIs.<br/><br/>
    In high-throughput microservices, calling third-party webhooks synchronously causes catastrophic cascading failures if a client's server is slow, rate-limited, or down. My engine solves this through four key architectural pillars:<br/>
    1. <b>Non-Blocking Ingestion</b>: Accepts events at <code>POST /api/webhooks/dispatch</code>, validates them, persists them into a PostgreSQL-backed durable queue, and returns <b>HTTP 202 Accepted in under 15ms</b>.<br/>
    2. <b>Asynchronous Reactive Fleet</b>: Background workers claim jobs atomically using optimistic concurrency and dispatch HTTP POST payloads via <b>Spring Reactive WebClient with Reactor Netty</b>. Crucially, no database transactions or connections are held open while waiting for external network I/O.<br/>
    3. <b>Exponential Backoff & Backpressure Handling</b>: Transient 5xx failures and timeouts automatically retry up to 3 times with exponential backoff (2s, 4s, 8s intervals), while permanent 4xx client errors abort immediately. It dynamically inspects and respects downstream <code>Retry-After</code> headers on HTTP 429.<br/>
    4. <b>Zero-Trust Security & Multi-Tier Rate Limiting</b>: Built-in <b>Bucket4j Token Bucket</b> limiters isolate both caller ingestion (100 req/s) and downstream destinations (10 req/s per domain) to prevent DDoS. To defend against Server-Side Request Forgery (SSRF) and DNS rebinding, internal subnets, loopbacks, and cloud metadata (<code>169.254.169.254</code>) are strictly prohibited via pre-flight checks and Netty DNS resolver hooks.<br/><br/>
    The entire system is containerized with Docker, covered by 26 automated integration tests, and deployed live with PostgreSQL on Railway.\"
    """
    pitch_table = Table([[Paragraph(pitch_text, body_style)]], colWidths=[504])
    pitch_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor("#F1F5F9")),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor("#CBD5E1")),
        ('PADDING', (0,0), (-1,-1), 8),
    ]))
    story.append(pitch_table)
    story.append(Spacer(1, 12))

    # SECTION 2: Architecture & Workflow
    story.append(Paragraph("2. Deep Dive Architecture & Component Lifecycle", h1_style))
    arch_desc = """
    <b>A. Ingestion Pipeline:</b><br/>
    • <b>Rate Limiter Check</b>: Client IP is extracted (honoring <code>X-Forwarded-For</code>) and evaluated against a Bucket4j Token Bucket (capacity: 100, refill: 100/sec). Exhaustion returns <code>HTTP 429</code> immediately.<br/>
    • <b>SSRF Validation</b>: <code>SafeUrlValidator</code> parses URI, verifies protocol (<code>https://</code> or <code>http://</code> in test), rejects credentials in URL, and resolves DNS to ensure the destination IP is not loopback (<code>127.0.0.1</code>), link-local, RFC 1918 private (<code>10.0.0.0/8</code>, <code>172.16.0.0/12</code>, <code>192.168.0.0/16</code>), or cloud metadata (<code>169.254.169.254</code>).<br/>
    • <b>Durable Persistence</b>: Job is persisted in PostgreSQL with status <code>PENDING</code>, <code>attempts = 0</code>, and <code>next_retry_at = NULL</code>.<br/>
    • <b>Immediate Response</b>: Returns <code>HTTP 202 Accepted</code> with JSON <code>{"status": "queued", "jobId": "..."}</code>.<br/><br/>
    <b>B. Worker Fleet & Dispatch Pipeline:</b><br/>
    • <b>Atomic Job Claiming</b>: Worker thread queries eligible jobs: <code>status IN ('PENDING', 'RETRYING') AND (next_retry_at IS NULL OR next_retry_at &lt;= NOW())</code> ordered by <code>created_at ASC</code>.<br/>
    • <b>Decoupled Network I/O</b>: Transaction ends immediately upon claiming status <code>PROCESSING</code>. The database connection is returned to HikariCP before Netty socket initiation.<br/>
    • <b>Per-Domain Rate Limiter</b>: Destination domain is extracted (e.g., <code>api.merchant.com</code>). Bucket4j ensures maximum 10 requests/sec per domain. If rate limit is exceeded, the job is deferred without penalty.<br/>
    • <b>Outcome Handling</b>:<br/>
      - <code>2xx Success</code>: Marked <code>SUCCESS</code>, latency and audit record written to <code>delivery_logs</code>.<br/>
      - <code>4xx Client Error</code>: Marked <code>FAILED</code> immediately (non-retryable, e.g. 401 Unauthorized, 404 Not Found).<br/>
      - <code>5xx / 429 / Timeout</code>: Increments attempt count. If <code>attempts &lt; 4</code>, schedules status <code>RETRYING</code> with backoff (2s, 4s, 8s) or downstream <code>Retry-After</code> value. If <code>attempts &gt;= 4</code>, status becomes permanently <code>FAILED</code>.
    """
    story.append(Paragraph(arch_desc, body_style))
    story.append(Spacer(1, 10))

    # Architecture Comparison Table
    table_data = [
        [Paragraph("<b>Component</b>", body_bold), Paragraph("<b>Our Implementation</b>", body_bold), Paragraph("<b>Traditional / Naive Approach</b>", body_bold), Paragraph("<b>Engineering Rationale</b>", body_bold)],
        [Paragraph("<b>HTTP Client</b>", body_style), Paragraph("Spring WebClient (Reactor Netty)", body_style), Paragraph("RestTemplate / Apache HttpClient", body_style), Paragraph("Non-blocking event loop prevents thread starvation when downstream endpoints are slow.", body_style)],
        [Paragraph("<b>Job Queue</b>", body_style), Paragraph("PostgreSQL Table with composite indexes", body_style), Paragraph("In-memory Java Queue (LinkedBlockingQueue)", body_style), Paragraph("Zero data loss across container restarts; jobs and audit history survive crashes.", body_style)],
        [Paragraph("<b>DB Transaction</b>", body_style), Paragraph("Short atomic transaction; closed before HTTP call", body_style), Paragraph("Transaction held across external HTTP request", body_style), Paragraph("Prevents database connection pool exhaustion when external calls take seconds.", body_style)],
        [Paragraph("<b>Security</b>", body_style), Paragraph("SafeUrlValidator + Netty DNS hook", body_style), Paragraph("No URL validation or basic regex check", body_style), Paragraph("Eliminates SSRF, cloud metadata exfiltration, and DNS rebinding attacks.", body_style)],
    ]
    t = Table(table_data, colWidths=[80, 130, 130, 164])
    t.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_bg_callout),
        ('GRID', (0,0), (-1,-1), 0.5, c_border),
        ('PADDING', (0,0), (-1,-1), 4),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
    ]))
    story.append(t)
    story.append(Spacer(1, 14))

    # SECTION 3: Top Technical Interview Questions & Model Answers
    story.append(Paragraph("3. Top 15 Technical Interview Questions & High-Impact Answers", h1_style))
    story.append(Paragraph("Be prepared to articulate these specific trade-offs and design justifications during the interview:", body_style))
    story.append(Spacer(1, 6))

    qa_list = [
        ("Q1: Why did you choose a database-backed queue instead of RabbitMQ or Kafka?",
         "For this service scope, a PostgreSQL durable queue provides strict ACID consistency, zero data loss across crashes, and unified transactions between job ingestion and delivery audit logs without managing external broker infrastructure. PostgreSQL's composite indexing on (status, next_retry_at, created_at) enables microsecond job lookups. In a system scaling past 50,000 req/s, we would evolve this to Kafka or AWS SQS with database CDC (Change Data Capture) via Debezium."),

        ("Q2: How do you prevent multiple worker instances from processing the same webhook job concurrently?",
         "Through atomic database state transitions. When workers claim pending jobs, they execute an atomic update (or SELECT ... FOR UPDATE SKIP LOCKED) that transitions the job status from PENDING/RETRYING to PROCESSING within an isolated microsecond transaction. Other workers encountering the locked rows skip them immediately, guaranteeing exactly-once claiming per attempt without race conditions."),

        ("Q3: What happens if a downstream client takes 10 seconds to respond? Does it block other webhooks?",
         "No. First, the ingestion endpoint is completely decoupled and returns 202 Accepted in under 15ms. Second, our worker fleet dispatches HTTP calls using Spring Reactive WebClient on Netty event loops (NIO). Unlike traditional blocking I/O (RestTemplate), Netty registers channel selectors and suspends execution without blocking an OS thread. Most importantly, the database connection is released before the HTTP request begins, so HikariCP connections are never starved by slow downstream targets."),

        ("Q4: What is SSRF and how did you prevent attackers from accessing internal infrastructure?",
         "Server-Side Request Forgery occurs when an attacker supplies a URL targeting internal private resources (e.g., http://169.254.169.254 to steal AWS/GCP instance IAM credentials, or http://localhost:5432 to probe internal services). I built SafeUrlValidator to enforce protocol whitelisting (https/http), strip userinfo credentials, and resolve DNS to evaluate the IP against RFC 1918 private CIDRs, loopbacks (127.0.0.1, ::1), and cloud metadata ranges. Any violation is rejected with HTTP 400 at ingestion."),

        ("Q5: What is DNS Rebinding and how does your application mitigate it?",
         "In a DNS Rebinding attack, an attacker configures a domain that resolves to a public IP during ingestion validation (passing initial checks), but when the background worker executes the HTTP request seconds later, the DNS TTL expires and resolves to an internal IP (like 127.0.0.1). I mitigated this by configuring Netty's HTTP connector with a custom DNS resolver hook that re-validates the resolved IP address immediately prior to socket connection establishment."),

        ("Q6: How does Bucket4j work and why did you use two separate rate limiters?",
         "Bucket4j implements the Token Bucket algorithm, where tokens refill at a fixed rate up to a maximum capacity. I implemented two distinct limiters because they serve opposite purposes: 1) Ingestion Rate Limiter (per client IP) protects our dispatcher engine from being overwhelmed by a flood of incoming requests. 2) Downstream Rate Limiter (per destination domain) protects third-party target endpoints from receiving rapid-fire bursts, adhering to respectful API citizenship."),

        ("Q7: How did you implement exponential backoff and why is jitter important?",
         "When a downstream server responds with 5xx or times out, retrying immediately risks causing a 'thundering herd' or retry storm that keeps the downed server dead. I implemented exponential backoff with schedules at 2s, 4s, and 8s intervals (next_retry_at = now + 2^attempt * baseDelay). In multi-node production clusters, adding randomized jitter (+/- 20% random variance) decorrelates simultaneous retry attempts across workers."),

        ("Q8: How does your system handle downstream HTTP 429 Too Many Requests?",
         "When a downstream server responds with 429, it often supplies a 'Retry-After' HTTP header specifying when it can accept requests again (either as delta-seconds like '60' or an HTTP-Date timestamp). My dispatcher inspects this header, parses the requested delay, and if it exceeds our standard backoff interval, reschedules the job for that exact time, respecting third-party backpressure."),

        ("Q9: Why does your retry logic permanently abort on HTTP 4xx errors?",
         "HTTP 4xx responses (such as 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 422 Unprocessable) indicate deterministic client errors. Retrying an invalid payload or bad authentication credentials will never succeed without manual payload alteration; retrying would simply waste network bandwidth and CPU cycles."),

        ("Q10: What is the purpose of Flyway in this project?",
         "Flyway provides version-controlled, repeatable, immutable database schema migrations (V1__init_schema.sql). In production distributed systems, relying on Hibernate's 'ddl-auto: update' is unsafe because it can cause race conditions, lock tables during deployments, or introduce unreviewed schema drifts. Flyway ensures schema evolution is auditable and synchronized across all environments."),

        ("Q11: How did you test asynchronous background retries in your automated test suite?",
         "I utilized Square's OkHttp MockWebServer combined with Awaitility. MockWebServer enqueued scripted sequences of HTTP responses (e.g., first attempt 503 Service Unavailable, second attempt 200 OK). Awaitility polled the database with timeouts (atMost 10 seconds) to assert state transitions from RETRYING to SUCCESS without flaky Thread.sleep calls."),

        ("Q12: How does the application handle graceful shutdown when a container is restarted?",
         "Spring Boot's 'server.shutdown: graceful' and thread pool 'setWaitForTasksToCompleteOnShutdown(true)' ensure that currently executing webhooks finish their in-flight network requests and write delivery logs before JVM shutdown. Any jobs remaining in PENDING or RETRYING status remain safe in PostgreSQL and are picked up immediately when the new container boots up."),

        ("Q13: How did you solve the Railway cloud deployment database connection?",
         "Cloud platforms like Railway, Render, and Heroku pass dynamic database connection strings in environment variables (DATABASE_URL, DATABASE_PRIVATE_URL, or PGHOST) using formats like postgres://user:pass@host:port/db. Spring Boot standard JDBC expects jdbc:postgresql://host:port/db. I implemented a custom DatabaseEnvironmentPostProcessor with Ordered.HIGHEST_PRECEDENCE that intercepts environment bootstrap, extracts credentials, translates URIs into standard Spring datasource properties, and injects them prior to Flyway and Hibernate initialization."),

        ("Q14: What composite indexes were added to the database and why?",
         "I indexed webhook_jobs on (status, next_retry_at, created_at). The background worker's query specifically filters by status IN ('PENDING', 'RETRYING') and next_retry_at <= NOW(), sorted by created_at. This composite index allows index-range scans without full-table scans. Additionally, delivery_logs is indexed on (job_id, attempt_number) for instantaneous audit log retrieval."),

        ("Q15: How would you scale this architecture to 100,000 webhooks per second?",
         "At 100k req/s, database contention becomes a bottleneck. The architecture evolves to: 1) Ingestion writes to a partitioned Kafka topic (keyed by destination domain to preserve order). 2) A distributed Redis cluster manages Token Bucket rate limits via Bucket4j Lettuce. 3) Scalable worker fleets (Kubernetes HPA) consume Kafka partitions. 4) Completed delivery logs are streamed via Kafka to an analytical datastore (ClickHouse or Amazon S3/Snowflake) via CDC.")
    ]

    for q, a in qa_list:
        story.append(Paragraph(q, q_style))
        story.append(Paragraph(a, a_style))

    # SECTION 4: Architecture Evolution & Edge Cases
    story.append(Spacer(1, 8))
    story.append(Paragraph("4. Production Hardening & Future Roadmap (Bonus Interview Topics)", h1_style))
    roadmap_text = """
    <b>When the interviewer asks:</b> <i>\"What would you add to this system before taking it to a tier-1 production environment?\"</i><br/><br/>
    • <b>HMAC SHA-256 Signatures</b>: Sign webhook payloads with a shared secret (<code>X-Hub-Signature-256: sha256=hex(hmac)</code>) so downstream clients can cryptographically verify authenticity and payload integrity.<br/>
    • <b>Dead Letter Queue (DLQ) & Alerting</b>: Webhooks that exhaust all 3 retries transition to a dedicated DLQ. Trigger automated PagerDuty / Slack alerts and provide a dead-letter replay API for operations teams.<br/>
    • <b>Mutual TLS (mTLS) Support</b>: Allow enterprise clients in fintech/healthcare to present client certificates for encrypted, mutually authenticated webhook delivery.<br/>
    • <b>Distributed Redis Rate Limiting</b>: Replace in-memory <code>ConcurrentHashMap</code> token buckets with Redis-backed distributed token buckets via Bucket4j Lettuce proxy to maintain strict rate limits across 50+ worker nodes.
    """
    story.append(Paragraph(roadmap_text, body_style))

    doc.build(story, canvasmaker=NumberedCanvas)
    print(f"Successfully generated: {filename}")

if __name__ == '__main__':
    output_pdf = "Distributed_Webhook_Dispatcher_Interview_Notes.pdf"
    build_pdf(output_pdf)

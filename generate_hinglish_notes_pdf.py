import os
import sys
from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable
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
            self.drawRightString(558, 750, "Complete Hinglish Flow & Architecture Guide")
            self.setStrokeColor(colors.HexColor("#CBD5E1"))
            self.setLineWidth(0.5)
            self.line(54, 744, 558, 744)
        
        # Footer (all pages)
        self.setStrokeColor(colors.HexColor("#CBD5E1"))
        self.setLineWidth(0.5)
        self.line(54, 45, 558, 45)
        
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#64748B"))
        self.drawString(54, 32, "Candidate: Shivam Jadon  |  Hinglish Comprehensive Project Flow Guide")
        page_str = f"Page {self._pageNumber} of {page_count}"
        self.drawRightString(558, 32, page_str)
        self.restoreState()

def build_hinglish_pdf(filename):
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        leftMargin=54,
        rightMargin=54,
        topMargin=54,
        bottomMargin=54
    )

    styles = getSampleStyleSheet()
    
    c_primary = colors.HexColor("#0F172A")    # Deep slate
    c_blue = colors.HexColor("#1D4ED8")       # Royal blue
    c_dark = colors.HexColor("#334155")       # Charcoal
    c_bg_callout = colors.HexColor("#F8FAFC") # Light gray-blue
    c_border = colors.HexColor("#CBD5E1")

    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=22,
        leading=26,
        textColor=c_primary,
        spaceAfter=4
    )
    
    subtitle_style = ParagraphStyle(
        'DocSubTitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=11,
        leading=15,
        textColor=c_blue,
        spaceAfter=12
    )
    
    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=14,
        leading=18,
        textColor=c_blue,
        spaceBefore=12,
        spaceAfter=6,
        keepWithNext=True
    )

    h2_style = ParagraphStyle(
        'Heading2_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=10.5,
        leading=14,
        textColor=c_primary,
        spaceBefore=8,
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

    q_style = ParagraphStyle(
        'Question_Style',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9.5,
        leading=13.5,
        textColor=c_primary,
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
        textColor=c_dark,
        spaceAfter=7
    )

    story = []

    # Title & Metadata
    story.append(Paragraph("Distributed Webhook Dispatcher Engine", title_style))
    story.append(Paragraph("A to Z In-Depth Project Summary & End-to-End System Flow (Hinglish)", subtitle_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=c_blue, spaceBefore=0, spaceAfter=8))

    meta_data = [
        [Paragraph("<b>Candidate:</b> Shivam Jadon", body_style), 
         Paragraph("<b>Tech Stack:</b> Java 17, Spring Boot 3.3.5, PostgreSQL, Netty WebClient", body_style)],
        [Paragraph("<b>Live URL:</b> <font color='#1D4ED8'>https://distributed-webhook-dispatcher-production-8cecda62.up.railway.app</font>", body_style),
         Paragraph("<b>GitHub:</b> <font color='#1D4ED8'>github.com/shivamjadon1/distributed-webhook-dispatcher</font>", body_style)]
    ]
    meta_table = Table(meta_data, colWidths=[240, 264])
    meta_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), c_bg_callout),
        ('BOX', (0,0), (-1,-1), 1, c_border),
        ('PADDING', (0,0), (-1,-1), 5),
    ]))
    story.append(meta_table)
    story.append(Spacer(1, 10))

    # SECTION 1: Problem & Solution
    story.append(Paragraph("1. Problem Kya Thi Aur Hamne Kya Banaya? (Real-World Scenario)", h1_style))
    p1 = """
    <b>Real-World Example:</b> Maano aap <b>Stripe, Razorpay ya Shopify</b> jaisa platform ho. Jab koi customer online payment karta hai, to aapko merchant (jaise Swiggy ya Zomato) ke server par ek <b>Webhook</b> (HTTP POST request) bhejna hota hai: <i>\"Payment successful ho gaya, order bana do!\"</i><br/><br/>
    <b>Ghalat (Naive) Approach:</b><br/>
    Agar checkout ke dauran hi merchant ke server par direct synchronous HTTP call karenge:<br/>
    • <b>Slow Server Problem:</b> Agar merchant ka server slow hai (10 second lagata hai response dene me), to customer ka checkout page bhi 10 second tak ghoomta rahega.<br/>
    • <b>Server Down / Data Loss:</b> Agar merchant ka server temporary down ya crash ho gaya, to webhook drop ho jayega aur merchant ko payment ka pata hi nahi chalega.<br/>
    • <b>DDoS / Traffic Spike:</b> Agar achanak 1000 orders ek sath aaye aur 1000 webhooks ek sath merchant par chale gaye, to unka server traffic se crush ho jayega.<br/><br/>
    <b>Hamara Solution (Distributed Webhook Dispatcher Engine):</b><br/>
    1. <b>Non-Blocking Ingestion:</b> Caller request bhejta hai, hum payload validate karke Database me save karte hain aur <b>under 15 milliseconds me HTTP 202 Accepted</b> bol kar caller ko turant free kar dete hain.<br/>
    2. <b>PostgreSQL Persistent Queue:</b> Saare jobs PostgreSQL me save hote hain. Agar server reboot ho ya crash ho, ek bhi webhook loss nahi hota.<br/>
    3. <b>Async Worker Fleet (Netty WebClient):</b> Background workers non-blocking reactive event-loops se HTTP dispatch karte hain (koi OS thread waste nahi hota).<br/>
    4. <b>Intelligent Retries (Exponential Backoff):</b> Downstream 5xx error ya timeout par <b>2s, 4s, 8s</b> ke gap par 3 baar retry hota hai. Receiver ke <code>Retry-After</code> header ko respect kiya jata hai.<br/>
    5. <b>Dual Rate Limiting (Bucket4j):</b> Ingestion par 100 req/s aur downstream per-destination-domain par 10 req/s ki token-bucket rate limits lagayi hain.
    """
    story.append(Paragraph(p1, body_style))
    story.append(Spacer(1, 8))

    # SECTION 2: End-to-End Flow
    story.append(Paragraph("2. End-to-End System Flow (Step-by-Step Execution)", h1_style))
    
    flow_steps = """
    <b>STEP 1: Ingestion Pipeline (POST /api/webhooks/dispatch)</b><br/>
    • Client request bhejta hai: <code>{ destinationUrl, eventId, payload }</code>.<br/>
    • <b>Ingestion Rate Limiter:</b> Client ke IP ke base par Bucket4j check karta hai (100 req/sec limit). Exceed hone par turant <code>HTTP 429 Too Many Requests</code>.<br/>
    • <b>SSRF & Security Defense (SafeUrlValidator):</b> Check karta hai ki protocol sirf <code>https://</code> ya <code>http://</code> ho. Agar kisi ne <code>127.0.0.1</code>, <code>localhost</code>, private IP (<code>192.168.x.x</code>), ya AWS cloud metadata IP (<code>169.254.169.254</code>) daala, to turant <code>HTTP 400 Bad Request</code> se block ho jata hai.<br/>
    • <b>Durable Storage:</b> Job ko <code>webhook_jobs</code> table me insert kiya jata hai (status: <code>PENDING</code>, attempts: <code>0</code>).<br/>
    • <b>Instant Response:</b> Caller ko turant <b>HTTP 202 Accepted</b> JSON mil jata hai: <code>{\"status\": \"queued\", \"jobId\": \"...\"}</code>.<br/><br/>
    
    <b>STEP 2: PostgreSQL Persistent Queue</b><br/>
    • Database table <code>webhook_jobs</code> me composite index hai: <code>(status, next_retry_at, created_at)</code>.<br/>
    • Is index ki wajah se workers bina full-table scan kiye microsecond me pending jobs fetch kar lete hain.<br/><br/>

    <b>STEP 3: Background Worker Fleet & Atomic Job Claiming</b><br/>
    • Scheduled worker thread batch me pending jobs claim karta hai (<code>LIMIT 25</code>).<br/>
    • Status ko atomically <code>PENDING</code> / <code>RETRYING</code> se <code>PROCESSING</code> mark karta hai taaki doosra worker same job pick na kare (Zero race conditions).<br/>
    • <b>GOLDEN RULE (DB Connection Safety):</b> Status change karte hi <b>Database transaction commit karke DB connection pool (HikariCP) ko wapas return kar diya jata hai!</b> HTTP call ke waqt koi database connection hold nahi rehta.<br/><br/>

    <b>STEP 4: Destination Domain Rate Limiting</b><br/>
    • Worker destination domain extract karta hai (jaise <code>api.swiggy.com</code>).<br/>
    • Bucket4j Token Bucket ensure karta hai ki us domain ko <b>maximum 10 requests per second</b> hi jayein.<br/>
    • Agar rate limit exceed hui, to worker job ko fail nahi karta aur retry count bhi waste nahi karta—bas job ko 1 second baad reschedule kar deta hai.<br/><br/>

    <b>STEP 5: Netty WebClient HTTP Dispatch (Non-Blocking I/O)</b><br/>
    • Spring WebClient (Reactor Netty) ke through target endpoint par HTTP POST call hoti hai.<br/>
    • <b>DNS Rebinding Defense:</b> Socket connect hone se theek pehle Netty ka custom DNS resolver check karta hai ki domain ka IP change ho kar local/private to nahi ho gaya.<br/>
    • Connect Timeout = 5000ms, Read Timeout = 5000ms.<br/><br/>

    <b>STEP 6: Outcome Resolution & Exponential Backoff</b><br/>
    • <b>2xx Success:</b> Job status <code>SUCCESS</code> mark hota hai.<br/>
    • <b>4xx Client Error (400, 401, 404):</b> Job status turant <code>FAILED</code> ho jata hai. Ghalat payload ko retry karke network bandwidth waste nahi karte.<br/>
    • <b>5xx Server Error / 429 Rate Limit / Timeout:</b> Attempt count increment hota hai. Agar attempts &lt; 4 hain, to job status <code>RETRYING</code> hota hai:<br/>
      - Attempt 1 fail: <b>2 seconds</b> baad retry.<br/>
      - Attempt 2 fail: <b>4 seconds</b> baad retry.<br/>
      - Attempt 3 fail: <b>8 seconds</b> baad retry.<br/>
      - Attempt 4 fail: Retries exhausted, status permanently <code>FAILED</code>.<br/>
    • <b>Retry-After Header:</b> Agar target 429 deta hai aur bolta hai <code>Retry-After: 30</code>, to dispatcher standard 2s ke badle <b>30 second baad</b> retry karta hai.<br/><br/>

    <b>STEP 7: Audit Logging & Observability</b><br/>
    • Har single delivery attempt ka record <code>delivery_logs</code> table me save hota hai (attempt number, HTTP status code, response body, latency, error message).<br/>
    • User <code>GET /api/webhooks/jobs/{jobId}/logs</code> se poori delivery history dekh sakta hai.
    """
    story.append(Paragraph(flow_steps, body_style))
    story.append(Spacer(1, 10))

    # SECTION 3: Outcome Decision Table
    story.append(Paragraph("3. Response Outcome & Decision Matrix", h1_style))
    matrix_data = [
        [Paragraph("<b>Response Event</b>", body_bold), Paragraph("<b>HTTP Code</b>", body_bold), Paragraph("<b>Engine Action</b>", body_bold), Paragraph("<b>Job Status</b>", body_bold), Paragraph("<b>Retry Hoga?</b>", body_bold)],
        [Paragraph("Successful Delivery", body_style), Paragraph("200, 201, 202", body_style), Paragraph("Delivery log save karo, flow complete", body_style), Paragraph("<font color='#16A34A'><b>SUCCESS</b></font>", body_style), Paragraph("Nahi", body_style)],
        [Paragraph("Client Error", body_style), Paragraph("400, 401, 403, 404", body_style), Paragraph("Permanent error, payload ghalat hai", body_style), Paragraph("<font color='#DC2626'><b>FAILED</b></font>", body_style), Paragraph("Nahi (No Retry)", body_style)],
        [Paragraph("Server Crash / Down", body_style), Paragraph("500, 502, 503, 504", body_style), Paragraph("Exponential backoff calculate karo", body_style), Paragraph("<font color='#D97706'><b>RETRYING</b></font>", body_style), Paragraph("Haan (2s, 4s, 8s)", body_style)],
        [Paragraph("Target Rate-Limited", body_style), Paragraph("429 Too Many Req", body_style), Paragraph("Retry-After header read karo", body_style), Paragraph("<font color='#D97706'><b>RETRYING</b></font>", body_style), Paragraph("Haan (Respect delay)", body_style)],
        [Paragraph("Network Timeout", body_style), Paragraph("Timeout / Refused", body_style), Paragraph("Network failure log karo, schedule retry", body_style), Paragraph("<font color='#D97706'><b>RETRYING</b></font>", body_style), Paragraph("Haan (2s, 4s, 8s)", body_style)],
        [Paragraph("Retries Exhausted", body_style), Paragraph("4 attempts done", body_style), Paragraph("Max retries cross ho gaye", body_style), Paragraph("<font color='#DC2626'><b>FAILED</b></font>", body_style), Paragraph("Nahi (Aborted)", body_style)],
    ]
    matrix_table = Table(matrix_data, colWidths=[100, 80, 164, 80, 80])
    matrix_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), c_bg_callout),
        ('GRID', (0,0), (-1,-1), 0.5, c_border),
        ('PADDING', (0,0), (-1,-1), 4.5),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
    ]))
    story.append(matrix_table)
    story.append(Spacer(1, 12))

    # SECTION 4: Top Interview Questions & Killer Answers
    story.append(Paragraph("4. Top 10 Technical Interview Questions & High-Scoring Hinglish Answers", h1_style))

    hinglish_qa = [
        ("Q1: Database queue kyu banaya? Kafka ya RabbitMQ kyu use nahi kiya?",
         "Answer: Is project ke scale par PostgreSQL queue hume do sabse bade fayde deta hai: Zero data loss across restarts aur unified ACID transactions without extra infrastructure overhead. Database me humne composite index (status, next_retry_at, created_at) lagaya hai jisse jobs microsecond me query ho jate hain. Agar 100k req/s ka massive scale ho, tab hum Kafka partitions aur Redis token buckets introduce karenge."),

        ("Q2: Agar destination server 10 second slow ho, to kya hamara API ya worker hang hoga?",
         "Answer: Bilkul nahi! Do reasons hain: Pehla, Ingestion API job ko queue me daal kar 15ms me '202 Accepted' de deta hai. Doosra, worker me humne Spring WebClient (Reactor Netty) use kiya hai jo non-blocking event loop par chalta hai (koi thread block nahi hota). Aur sabse important: HTTP request shuru hone se pehle hum database connection HikariCP pool ko return kar dete hain, isliye DB connections kabhi exhaust nahi hote."),

        ("Q3: Multiple workers hone par ek hi job do baar process kyu nahi hoti?",
         "Answer: Atomic state transition ki wajah se. Jab worker database se pending jobs fetch karta hai, wo microsecond transaction me status ko 'PENDING'/'RETRYING' se badal kar 'PROCESSING' kar deta hai (SELECT FOR UPDATE SKIP LOCKED pattern). Doosra worker us job ko skip kar deta hai, isliye exactly-once execution guarantee rehti hai."),

        ("Q4: SSRF aur DNS Rebinding attacks se kaise bachaya?",
         "Answer: Server-Side Request Forgery se bachne ke liye SafeUrlValidator check karta hai ki protocol sirf http/https ho, aur domain ka resolved IP 127.0.0.1, private LAN, ya AWS cloud metadata IP (169.254.169.254) na ho. DNS Rebinding se bachne ke liye humne Netty connector me custom DNS resolver hook lagaya hai jo socket connection banne se theek pehle IP ko dubara re-validate karta hai."),

        ("Q5: 4xx client errors aane par retry kyu nahi karte?",
         "Answer: 4xx errors (jaise 400 Bad Request, 401 Unauthorized, 404 Not Found) deterministic client mistakes hoti hain. Agar payload ka schema ghalat hai ya API key invalid hai, to 10 baar retry karne se bhi server 4xx hi dega. Isliye hum retry count aur network bandwidth waste kiye bina use attempt 1 par hi permanently FAILED mark kar dete hain."),

        ("Q6: Bucket4j Rate Limiter kaise kaam karta hai aur do alag limiters kyu lagaye?",
         "Answer: Bucket4j Token Bucket algorithm par chalta hai jahan tokens continuous refill hote hain. Humne do alag limiters lagaye hain: 1) Ingestion Limiter (100 req/s per client IP) jo hamare system ko DDoS aur traffic spike se protect karta hai. 2) Downstream Limiter (10 req/s per destination domain) jo ensure karta hai ki kisi third-party merchant ka server hamare webhooks se flood na ho jaye."),

        ("Q7: Exponential backoff me Jitter kyu zaruri hota hai?",
         "Answer: Agar 1000 webhooks ek sath fail hue aur sabka retry gap exactly 2s, 4s, 8s hua, to 2 second baad wo 1000 requests dubara ek sath receiver par hit karengi jisse receiver dubara crash ho jayega (ise 'Thundering Herd' kehte hain). Jitter (+/- 20% random variance) add karne se retries evenly distribute ho jati hain."),

        ("Q8: Railway par deployment me database connection problem ko kaise solve kiya?",
         "Answer: Railway dynamically environment variables provide karta hai (DATABASE_URL = postgres://user:pass@host:port/db), jabki Spring Boot JDBC expects jdbc:postgresql://... format. Humne custom DatabaseEnvironmentPostProcessor likha (Ordered.HIGHEST_PRECEDENCE ke sath) jo container boot hone par URL parse karke username, password, host extract karta hai aur Spring DataSource properties me inject kar deta hai."),

        ("Q9: Flyway ka kya role hai aur hibernate 'ddl-auto: update' kyu nahi use kiya?",
         "Answer: Production me ddl-auto: update use karna dangerous hota hai kyunki wo bina review schema alter kar sakta hai ya multi-instance startup par table locks laga sakta hai. Flyway immutable versioned SQL migration scripts (V1__init_schema.sql) execute karta hai, jisse schema changes version-controlled aur predictable rehte hain."),

        ("Q10: Delivery audit logs ka structure kaisa hai aur isse kya fayda hota hai?",
         "Answer: delivery_logs table me job_id, attempt_number, response_code, status, error_message, response_body aur attempted_at timestamp store hota hai. Isse client ko 100% transparency milti hai: wo GET /api/webhooks/jobs/{id}/logs call karke dekh sakta hai ki har attempt kis second hua tha aur downstream server ne kya response diya tha.")
    ]

    for q, a in hinglish_qa:
        story.append(Paragraph(q, q_style))
        story.append(Paragraph(a, a_style))

    # SECTION 5: Live Verification & Testing
    story.append(Spacer(1, 8))
    story.append(Paragraph("5. Live Public Deployment & Testing cURLs", h1_style))
    test_text = """
    • <b>Live Public Endpoint:</b> <code>https://distributed-webhook-dispatcher-production-8cecda62.up.railway.app</code><br/>
    • <b>Interactive Dashboard:</b> <code>https://distributed-webhook-dispatcher-production-8cecda62.up.railway.app/</code><br/>
    • <b>Health Check:</b> <code>https://distributed-webhook-dispatcher-production-8cecda62.up.railway.app/api/health</code><br/>
    • <b>GitHub Repo:</b> <code>https://github.com/shivamjadon1/distributed-webhook-dispatcher</code><br/><br/>
    <b>Test cURL (Returns 202 Accepted immediately & delivers in background):</b><br/>
    <code>curl -X POST https://distributed-webhook-dispatcher-production-8cecda62.up.railway.app/api/webhooks/dispatch \\<br/>
    &nbsp;&nbsp;-H \"Content-Type: application/json\" \\<br/>
    &nbsp;&nbsp;-d '{\"destinationUrl\": \"https://httpbin.org/post\", \"eventId\": \"evt_101\", \"payload\": {\"status\": \"success\"}}'</code>
    """
    story.append(Paragraph(test_text, body_style))

    doc.build(story, canvasmaker=NumberedCanvas)
    print(f"Successfully generated Hinglish PDF: {filename}")

if __name__ == '__main__':
    output_pdf = "Distributed_Webhook_Dispatcher_Hinglish_Summary.pdf"
    build_hinglish_pdf(output_pdf)

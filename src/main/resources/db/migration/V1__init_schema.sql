-- Flyway Database Migration: V1__init_schema.sql
-- Distributed Webhook Dispatcher Engine Schema

CREATE TABLE IF NOT EXISTS webhook_jobs (
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

CREATE INDEX IF NOT EXISTS idx_webhook_jobs_queue 
    ON webhook_jobs (status, next_retry_at, created_at);

CREATE INDEX IF NOT EXISTS idx_webhook_jobs_event_id 
    ON webhook_jobs (event_id);

CREATE TABLE IF NOT EXISTS delivery_logs (
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

CREATE INDEX IF NOT EXISTS idx_delivery_logs_job_id 
    ON delivery_logs (job_id, attempt_number);

CREATE INDEX IF NOT EXISTS idx_delivery_logs_attempted_at 
    ON delivery_logs (attempted_at);

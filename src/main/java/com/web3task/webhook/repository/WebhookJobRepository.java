package com.web3task.webhook.repository;

import com.web3task.webhook.entity.JobStatus;
import com.web3task.webhook.entity.WebhookJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebhookJobRepository extends JpaRepository<WebhookJob, String> {

    Optional<WebhookJob> findByEventId(String eventId);

    @Query(value = """
        SELECT id FROM webhook_jobs 
        WHERE status IN ('PENDING', 'RETRYING') 
          AND (next_retry_at IS NULL OR next_retry_at <= :now) 
        ORDER BY created_at ASC 
        LIMIT :batchSize
        """, nativeQuery = true)
    List<String> findDueJobIds(@Param("now") Instant now, @Param("batchSize") int batchSize);

    @Modifying
    @Query("""
        UPDATE WebhookJob j 
        SET j.status = :newStatus, j.updatedAt = :now 
        WHERE j.id = :id AND j.status IN (:expectedStatuses)
        """)
    int updateJobStatusIfIn(
        @Param("id") String id,
        @Param("expectedStatuses") Collection<JobStatus> expectedStatuses,
        @Param("newStatus") JobStatus newStatus,
        @Param("now") Instant now
    );

    @Modifying
    @Query("""
        UPDATE WebhookJob j 
        SET j.status = :status, 
            j.attempts = :attempts, 
            j.nextRetryAt = :nextRetryAt, 
            j.updatedAt = :now 
        WHERE j.id = :id
        """)
    int updateJobExecutionResult(
        @Param("id") String id,
        @Param("status") JobStatus status,
        @Param("attempts") int attempts,
        @Param("nextRetryAt") Instant nextRetryAt,
        @Param("now") Instant now
    );

    List<WebhookJob> findByStatus(JobStatus status);
}

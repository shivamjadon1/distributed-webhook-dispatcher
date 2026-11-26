package com.web3task.webhook.repository;

import com.web3task.webhook.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, String> {

    List<DeliveryLog> findByJobIdOrderByAttemptNumberAsc(String jobId);

    long countByJobId(String jobId);
}

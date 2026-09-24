package edu.wylie.crs.repository;

import edu.wylie.crs.entity.BillingRetryJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface BillingRetryJobRepository extends JpaRepository<BillingRetryJob, Long> {
    List<BillingRetryJob> findByTermOrderByIdAsc(String term);

    List<BillingRetryJob> findByStatusAndNextRetryAtLessThanEqualOrderByIdAsc(String status, Instant dueAt);

    long countByTermAndStatus(String term, String status);
}

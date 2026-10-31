package com.brightloop.modules.billing;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BillingRunRepository extends JpaRepository<BillingRun, Long> {
  Optional<BillingRun> findByIdempotencyKey(String idempotencyKey);

  @Query(
      "select r from BillingRun r where (:status is null or r.status = :status) and"
          + " (lower(r.idempotencyKey) like concat('%', :q, '%') or lower(r.triggeredBy) like"
          + " concat('%', :q, '%') or lower(r.triggerType) like concat('%', :q, '%'))")
  Page<BillingRun> search(String q, String status, Pageable pageable);

  @EntityGraph(attributePaths = {"items"})
  Optional<BillingRun> findWithItemsById(Long id);
}

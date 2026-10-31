package com.brightloop.modules.quotation;

import com.brightloop.config.AccountStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface QuotationTemplateRepository extends JpaRepository<QuotationTemplate, Long> {
  @EntityGraph(attributePaths = {"plan"})
  @Query(
      "select t from QuotationTemplate t where (:status is null or t.status = :status) and"
          + " (:planId is null or t.plan.id = :planId) and (lower(t.name) like concat('%', :q,"
          + " '%') or lower(t.planName) like concat('%', :q, '%') or lower(coalesce(t.description,"
          + " '')) like concat('%', :q, '%'))")
  Page<QuotationTemplate> search(
      String q, AccountStatus status, Long planId, Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select t from QuotationTemplate t where t.id = :id")
  Optional<QuotationTemplate> lockById(Long id);

  Optional<QuotationTemplate> findByNameIgnoreCase(String name);
}

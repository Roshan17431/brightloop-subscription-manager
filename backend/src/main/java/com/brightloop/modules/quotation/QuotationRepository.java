package com.brightloop.modules.quotation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface QuotationRepository extends JpaRepository<Quotation, Long> {
  @EntityGraph(attributePaths = {"customer", "plan", "template", "convertedSubscription"})
  @Query(
      "select q from Quotation q where (:customerId is null or q.customer.id = :customerId) and"
          + " (:templateId is null or q.template.id = :templateId) and (:state is null or q.state"
          + " = :state) and (lower(q.quotationNumber) like concat('%', :search, '%') or"
          + " lower(q.customerName) like concat('%', :search, '%') or lower(q.planName) like"
          + " concat('%', :search, '%') or lower(coalesce(q.templateName, '')) like concat('%',"
          + " :search, '%'))")
  Page<Quotation> search(
      String search,
      QuotationState state,
      Long customerId,
      Long templateId,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select q from Quotation q where q.id = :id")
  Optional<Quotation> lockById(Long id);
}

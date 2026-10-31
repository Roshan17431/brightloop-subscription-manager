package com.brightloop.modules.invoice;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
  @EntityGraph(attributePaths = {"customer", "subscription", "plan"})
  @Query(
      "select i from Invoice i where (:customerId is null or i.customer.id = :customerId) and"
          + " (:subscriptionId is null or i.subscription.id = :subscriptionId) and (:state is null"
          + " or i.state = :state) and (:outstandingOnly = false or (i.state ="
          + " com.brightloop.modules.invoice.InvoiceState.CONFIRMED and i.amountPaid < i.total))"
          + " and (:overdueOnly = false or (i.state ="
          + " com.brightloop.modules.invoice.InvoiceState.CONFIRMED and i.amountPaid < i.total"
          + " and i.dueDate < :asOfDate)) and (:partiallyPaidOnly = false or (i.state ="
          + " com.brightloop.modules.invoice.InvoiceState.CONFIRMED and i.amountPaid > 0 and"
          + " i.amountPaid < i.total)) and (lower(i.invoiceNumber) like concat('%', :q, '%') or"
          + " lower(i.subscriptionNumber) like concat('%', :q, '%') or lower(i.customerName) like"
          + " concat('%', :q, '%') or lower(i.planName) like concat('%', :q, '%'))")
  Page<Invoice> search(
      String q,
      InvoiceState state,
      Long subscriptionId,
      Long customerId,
      boolean outstandingOnly,
      boolean overdueOnly,
      boolean partiallyPaidOnly,
      LocalDate asOfDate,
      Pageable pageable);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from Invoice i where i.id = :id")
  Optional<Invoice> lockById(Long id);

  Optional<Invoice> findBySubscriptionIdAndBillingPeriodStart(
      Long subscriptionId, LocalDate billingPeriodStart);

  Optional<Invoice> findByIdempotencyKey(String idempotencyKey);
}

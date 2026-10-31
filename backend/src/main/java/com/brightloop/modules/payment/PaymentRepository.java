package com.brightloop.modules.payment;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  @EntityGraph(attributePaths = {"invoice", "customer", "invoice.subscription", "invoice.plan"})
  @Query(
      "select p from Payment p where (:customerId is null or p.customer.id = :customerId) and"
          + " (:invoiceId is null or p.invoice.id = :invoiceId) and (:method is null or p.method ="
          + " :method) and (:status is null or p.status = :status) and (:fromDate is null or"
          + " p.paymentDate >= :fromDate) and (:toDate is null or p.paymentDate <= :toDate) and"
          + " (lower(p.paymentReference) like concat('%', :q, '%') or lower(p.invoiceNumber) like"
          + " concat('%', :q, '%') or lower(p.customerName) like concat('%', :q, '%'))")
  Page<Payment> search(
      String q,
      Long invoiceId,
      Long customerId,
      PaymentMethod method,
      PaymentStatus status,
      LocalDate fromDate,
      LocalDate toDate,
      Pageable pageable);

  @EntityGraph(attributePaths = {"invoice", "customer", "invoice.subscription", "invoice.plan"})
  @Query("select p from Payment p where p.invoice.id = :invoiceId order by p.paymentDate asc, p.id asc")
  List<Payment> findByInvoiceIdOrderByPaymentDateAscIdAsc(Long invoiceId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Payment p where p.id = :id")
  Optional<Payment> lockById(Long id);

  Optional<Payment> findByIdempotencyKey(String idempotencyKey);

  Optional<Payment> findByPaymentReferenceIgnoreCase(String paymentReference);

  @Query(
      "select coalesce(sum(p.amount), 0) from Payment p where p.invoice.id = :invoiceId and"
          + " p.status = com.brightloop.modules.payment.PaymentStatus.COMPLETED")
  BigDecimal sumCompletedAmountByInvoiceId(Long invoiceId);
}

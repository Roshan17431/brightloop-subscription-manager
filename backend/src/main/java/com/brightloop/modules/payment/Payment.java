package com.brightloop.modules.payment;

import com.brightloop.config.RecordEntity;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.invoice.Invoice;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "payments")
public class Payment extends RecordEntity {
  @Version public long version;

  @Column(name = "payment_reference", nullable = false, unique = true, length = 100)
  public String paymentReference;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
  public String idempotencyKey;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  public Invoice invoice;

  @Column(name = "invoice_number", nullable = false, length = 50)
  public String invoiceNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  public Customer customer;

  @Column(name = "customer_name", nullable = false, length = 120)
  public String customerName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  public PaymentMethod method;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal amount;

  @Column(name = "payment_date", nullable = false)
  public LocalDate paymentDate;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  public PaymentStatus status = PaymentStatus.COMPLETED;

  @Column(length = 1000)
  public String note;

  @Column(name = "recorded_by", nullable = false, length = 254)
  public String recordedBy;

  @Column(name = "reversed_at")
  public Instant reversedAt;

  @Column(name = "reversed_by", length = 254)
  public String reversedBy;

  @Column(name = "reversal_reason", length = 500)
  public String reversalReason;

  @Column(name = "reversal_reference", unique = true, length = 100)
  public String reversalReference;
}

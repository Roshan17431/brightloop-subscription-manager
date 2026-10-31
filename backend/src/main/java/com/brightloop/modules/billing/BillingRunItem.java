package com.brightloop.modules.billing;

import com.brightloop.config.RecordEntity;
import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "billing_run_items")
public class BillingRunItem extends RecordEntity {
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "billing_run_id", nullable = false)
  public BillingRun billingRun;

  @Column(name = "subscription_id", nullable = false)
  public Long subscriptionId;

  @Column(name = "subscription_number", nullable = false, length = 50)
  public String subscriptionNumber;

  @Column(name = "billing_period_start", nullable = false)
  public LocalDate billingPeriodStart;

  @Column(nullable = false, length = 24)
  public String status;

  @Column(name = "invoice_id")
  public Long invoiceId;

  @Column(name = "invoice_number", length = 50)
  public String invoiceNumber;

  @Column(name = "attempt_count", nullable = false)
  public int attemptCount = 1;

  @Column(length = 1000)
  public String message;
}

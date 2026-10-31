package com.brightloop.modules.billing;

import com.brightloop.config.RecordEntity;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "billing_runs")
public class BillingRun extends RecordEntity {
  @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
  public String idempotencyKey;

  @Column(name = "billing_date", nullable = false)
  public LocalDate billingDate;

  @Column(name = "trigger_type", nullable = false, length = 24)
  public String triggerType;

  @Column(name = "triggered_by", nullable = false, length = 254)
  public String triggeredBy;

  @Column(nullable = false, length = 32)
  public String status = "RUNNING";

  @Column(name = "total_evaluated", nullable = false)
  public int totalEvaluated;

  @Column(name = "invoices_generated", nullable = false)
  public int invoicesGenerated;

  @Column(name = "skipped_count", nullable = false)
  public int skippedCount;

  @Column(name = "failed_count", nullable = false)
  public int failedCount;

  @Column(name = "expired_closed_count", nullable = false)
  public int expiredClosedCount;

  @Column(name = "started_at", nullable = false)
  public Instant startedAt;

  @Column(name = "completed_at")
  public Instant completedAt;

  @Column(name = "error_summary", length = 2000)
  public String errorSummary;

  @OneToMany(mappedBy = "billingRun", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<BillingRunItem> items = new ArrayList<>();
}

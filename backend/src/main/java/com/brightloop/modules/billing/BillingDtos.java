package com.brightloop.modules.billing;

import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class BillingDtos {
  private BillingDtos() {}

  public record RunRequest(
      LocalDate billingDate,
      @Size(max = 120) String idempotencyKey,
      Long subscriptionId) {}

  public record ItemView(
      Long id,
      Long subscriptionId,
      String subscriptionNumber,
      LocalDate billingPeriodStart,
      String status,
      Long invoiceId,
      String invoiceNumber,
      int attemptCount,
      String message,
      Instant createdAt) {}

  public record RunView(
      Long id,
      String idempotencyKey,
      LocalDate billingDate,
      String triggerType,
      String triggeredBy,
      String status,
      int totalEvaluated,
      int invoicesGenerated,
      int skippedCount,
      int failedCount,
      int expiredClosedCount,
      Instant startedAt,
      Instant completedAt,
      String errorSummary,
      List<ItemView> items) {}

  public static RunView view(BillingRun r, List<BillingRunItem> items) {
    return new RunView(
        r.id,
        r.idempotencyKey,
        r.billingDate,
        r.triggerType,
        r.triggeredBy,
        r.status,
        r.totalEvaluated,
        r.invoicesGenerated,
        r.skippedCount,
        r.failedCount,
        r.expiredClosedCount,
        r.startedAt,
        r.completedAt,
        r.errorSummary,
        items == null
            ? List.of()
            : items.stream()
                .map(
                    i ->
                        new ItemView(
                            i.id,
                            i.subscriptionId,
                            i.subscriptionNumber,
                            i.billingPeriodStart,
                            i.status,
                            i.invoiceId,
                            i.invoiceNumber,
                            i.attemptCount,
                            i.message,
                            i.createdAt))
                .toList());
  }
}

package com.brightloop.modules.invoice;

import com.brightloop.modules.plan.BillingPeriod;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class InvoiceDtos {
  private InvoiceDtos() {}

  public record Transition(@NotNull InvoiceState state) {}

  public record FollowUpInput(@NotBlank @Size(max = 1000) String notes) {}

  public record LineView(
      Long id,
      Long productId,
      String productName,
      Long variantId,
      String variantName,
      int quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountAmount,
      Long discountId,
      String discountName,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxRate,
      Long taxRuleId,
      String taxRuleName,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}

  public record View(
      Long id,
      String invoiceNumber,
      Long subscriptionId,
      String subscriptionNumber,
      Long customerId,
      String customerName,
      String customerEmail,
      String customerPhone,
      String customerCompany,
      String billingAddress,
      Long planId,
      String planName,
      BillingPeriod billingPeriod,
      LocalDate billingPeriodStart,
      LocalDate billingPeriodEnd,
      LocalDate issueDate,
      LocalDate dueDate,
      String paymentTerms,
      InvoiceState state,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal discountAmount,
      Long discountId,
      String discountName,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amountPaid,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal outstandingAmount,
      String paymentStatus,
      boolean partiallyPaid,
      boolean overdue,
      long daysOverdue,
      String idempotencyKey,
      Long billingRunId,
      String paymentReference,
      Instant paidAt,
      Instant confirmedAt,
      Instant cancelledAt,
      Instant sentAt,
      Instant lastFollowupAt,
      String followupNotes,
      String followupBy,
      String notes,
      List<LineView> lines,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Invoice i) {
    return view(i, LocalDate.now());
  }

  public static View view(Invoice i, LocalDate asOfDate) {
    BigDecimal paid = i.amountPaid != null ? i.amountPaid : BigDecimal.ZERO.setScale(2);
    BigDecimal total = i.total != null ? i.total : BigDecimal.ZERO.setScale(2);
    BigDecimal outstanding =
        (i.state == InvoiceState.CANCELLED || i.state == InvoiceState.PAID)
            ? BigDecimal.ZERO.setScale(2)
            : total.subtract(paid).max(BigDecimal.ZERO.setScale(2));
    boolean partial =
        i.state == InvoiceState.CONFIRMED && paid.signum() > 0 && paid.compareTo(total) < 0;
    String paymentStatus =
        switch (i.state) {
          case CANCELLED -> "CANCELLED";
          case PAID -> "PAID";
          case DRAFT, CONFIRMED -> partial ? "PARTIALLY_PAID" : "UNPAID";
        };
    LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();
    boolean isOverdue =
        i.state == InvoiceState.CONFIRMED
            && outstanding.signum() > 0
            && i.dueDate != null
            && i.dueDate.isBefore(evalDate);
    long daysOverdue =
        isOverdue ? Math.max(0L, ChronoUnit.DAYS.between(i.dueDate, evalDate)) : 0L;

    return new View(
        i.id,
        i.invoiceNumber,
        i.subscription.getId(),
        i.subscriptionNumber,
        i.customer.getId(),
        i.customerName,
        i.customerEmail,
        i.customerPhone,
        i.customerCompany,
        i.billingAddress,
        i.plan.getId(),
        i.planName,
        i.billingPeriod,
        i.billingPeriodStart,
        i.billingPeriodEnd,
        i.issueDate,
        i.dueDate,
        i.paymentTerms,
        i.state,
        i.subtotal,
        i.discountAmount,
        i.discount == null ? null : i.discount.getId(),
        i.discountName,
        i.taxAmount,
        i.total,
        paid,
        outstanding,
        paymentStatus,
        partial,
        isOverdue,
        daysOverdue,
        i.idempotencyKey,
        i.billingRunId,
        i.paymentReference,
        i.paidAt,
        i.confirmedAt,
        i.cancelledAt,
        i.sentAt,
        i.lastFollowupAt,
        i.followupNotes,
        i.followupBy,
        i.notes,
        i.lines.stream()
            .map(
                l ->
                    new LineView(
                        l.id,
                        l.product.getId(),
                        l.productName,
                        l.variant == null ? null : l.variant.getId(),
                        l.variantName,
                        l.quantity,
                        l.unitPrice,
                        l.subtotal,
                        l.discountAmount,
                        l.discount == null ? null : l.discount.getId(),
                        l.discountName,
                        l.taxRate,
                        l.taxRule == null ? null : l.taxRule.getId(),
                        l.taxRuleName,
                        l.taxAmount,
                        l.amount))
            .toList(),
        i.createdAt,
        i.updatedAt);
  }
}

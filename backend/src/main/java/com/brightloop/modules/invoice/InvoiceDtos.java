package com.brightloop.modules.invoice;

import com.brightloop.modules.plan.BillingPeriod;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class InvoiceDtos {
  private InvoiceDtos() {}

  public record Transition(@NotNull InvoiceState state) {}

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
      String idempotencyKey,
      Long billingRunId,
      String paymentReference,
      Instant paidAt,
      Instant confirmedAt,
      Instant cancelledAt,
      Instant sentAt,
      String notes,
      List<LineView> lines,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Invoice i) {
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
        i.idempotencyKey,
        i.billingRunId,
        i.paymentReference,
        i.paidAt,
        i.confirmedAt,
        i.cancelledAt,
        i.sentAt,
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

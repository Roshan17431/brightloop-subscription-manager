package com.brightloop.modules.quotation;

import com.brightloop.config.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class QuotationDtos {
  private QuotationDtos() {}

  public record TemplateLineInput(
      @NotNull Long productId,
      Long variantId,
      @Min(1) @Max(1000000) int quantity,
      @DecimalMin("0.00")
          @Digits(integer = 12, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal unitPrice,
      @NotNull
          @DecimalMin("0.00")
          @DecimalMax("100.00")
          @Digits(integer = 3, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal taxRate) {}

  public record TemplateInput(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 2000) String description,
      @NotNull Long planId,
      @Min(1) @Max(3650) int validityDays,
      @NotBlank @Size(max = 1000) String paymentTerms,
      @NotNull AccountStatus status,
      @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid TemplateLineInput> lines) {}

  public record TemplateLineView(
      Long id,
      Long productId,
      String productName,
      Long variantId,
      String variantName,
      int quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxRate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}

  public record TemplateView(
      Long id,
      String name,
      String description,
      Long planId,
      String planName,
      String billingPeriod,
      int validityDays,
      String paymentTerms,
      AccountStatus status,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal estimatedTotal,
      List<TemplateLineView> lines,
      Instant createdAt,
      Instant updatedAt) {}

  public record GenerateInput(
      Long templateId,
      @NotNull Long customerId,
      LocalDate issueDate,
      LocalDate validUntil,
      @NotNull LocalDate startDate,
      @NotNull LocalDate expiryDate,
      @Size(max = 1000) String paymentTerms,
      @Size(max = 2000) String notes,
      List<@NotNull @Valid TemplateLineInput> lines) {}

  public record ConfirmInput(LocalDate asOfDate) {}

  public record ConvertInput(
      LocalDate startDate,
      LocalDate expiryDate,
      Boolean activateImmediately,
      LocalDate asOfDate) {}

  public record QuotationLineView(
      Long id,
      Long productId,
      String productName,
      Long variantId,
      String variantName,
      int quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxRate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}

  public record QuotationView(
      Long id,
      String quotationNumber,
      Long templateId,
      String templateName,
      Long customerId,
      String customerName,
      Long planId,
      String planName,
      String billingPeriod,
      LocalDate issueDate,
      LocalDate validUntil,
      LocalDate startDate,
      LocalDate expiryDate,
      String paymentTerms,
      QuotationState state,
      boolean expired,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal subtotal,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxAmount,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
      String notes,
      Long convertedSubscriptionId,
      String convertedSubscriptionNumber,
      Instant confirmedAt,
      Instant convertedAt,
      Instant cancelledAt,
      String createdBy,
      List<QuotationLineView> lines,
      Instant createdAt,
      Instant updatedAt) {}

  public static TemplateView viewTemplate(QuotationTemplate t) {
    var plan =
        t.plan == null
            ? null
            : org.hibernate.Hibernate.unproxy(t.plan, com.brightloop.modules.plan.Plan.class);
    var total =
        t.lines.stream()
            .map(l -> l.amount)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    return new TemplateView(
        t.id,
        t.name,
        t.description,
        t.plan.getId(),
        t.planName,
        plan != null && plan.billingPeriod != null ? plan.billingPeriod.name() : null,
        t.validityDays,
        t.paymentTerms,
        t.status,
        total,
        t.lines.stream()
            .map(
                l ->
                    new TemplateLineView(
                        l.id,
                        l.product.getId(),
                        l.productName,
                        l.variant == null ? null : l.variant.getId(),
                        l.variantName,
                        l.quantity,
                        l.unitPrice,
                        l.taxRate,
                        l.amount))
            .toList(),
        t.createdAt,
        t.updatedAt);
  }

  public static QuotationView viewQuotation(Quotation q) {
    return viewQuotation(q, LocalDate.now());
  }

  public static QuotationView viewQuotation(Quotation q, LocalDate asOfDate) {
    var plan =
        q.plan == null
            ? null
            : org.hibernate.Hibernate.unproxy(q.plan, com.brightloop.modules.plan.Plan.class);
    LocalDate evalDate = asOfDate != null ? asOfDate : LocalDate.now();
    boolean isExpired =
        q.state == QuotationState.EXPIRED
            || ((q.state == QuotationState.DRAFT
                    || q.state == QuotationState.SENT
                    || q.state == QuotationState.CONFIRMED)
                && q.validUntil != null
                && q.validUntil.isBefore(evalDate));
    return new QuotationView(
        q.id,
        q.quotationNumber,
        q.template == null ? null : q.template.getId(),
        q.templateName,
        q.customer.getId(),
        q.customerName,
        q.plan.getId(),
        q.planName,
        plan != null && plan.billingPeriod != null ? plan.billingPeriod.name() : null,
        q.issueDate,
        q.validUntil,
        q.startDate,
        q.expiryDate,
        q.paymentTerms,
        q.state,
        isExpired,
        q.subtotal,
        q.taxAmount,
        q.total,
        q.notes,
        q.convertedSubscription == null ? null : q.convertedSubscription.getId(),
        q.convertedSubscriptionNumber,
        q.confirmedAt,
        q.convertedAt,
        q.cancelledAt,
        q.createdBy,
        q.lines.stream()
            .map(
                l ->
                    new QuotationLineView(
                        l.id,
                        l.product.getId(),
                        l.productName,
                        l.variant == null ? null : l.variant.getId(),
                        l.variantName,
                        l.quantity,
                        l.unitPrice,
                        l.subtotal,
                        l.taxRate,
                        l.taxAmount,
                        l.amount))
            .toList(),
        q.createdAt,
        q.updatedAt);
  }
}

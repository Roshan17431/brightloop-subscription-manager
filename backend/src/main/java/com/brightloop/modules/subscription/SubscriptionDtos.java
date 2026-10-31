package com.brightloop.modules.subscription;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class SubscriptionDtos {
  private SubscriptionDtos() {}

  public record LineInput(
      @NotNull Long productId,
      Long variantId,
      @Min(1) @Max(1000000) int quantity,
      @NotNull
          @DecimalMin("0.00")
          @DecimalMax("100.00")
          @Digits(integer = 3, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal taxRate) {}

  public record Input(
      Long customerId,
      @NotNull Long planId,
      @NotNull LocalDate startDate,
      @NotNull LocalDate expiryDate,
      @NotBlank @Size(max = 1000) String paymentTerms,
      @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid LineInput> lines) {}

  public record Transition(@NotNull SubscriptionState state) {}

  public record PauseInput(LocalDate pauseDate, @Size(max = 500) String reason) {}

  public record ResumeInput(LocalDate resumeDate) {}

  public record RenewInput(
      LocalDate newExpiryDate,
      @Min(1) @Max(120) Integer periods,
      @Size(max = 120) String idempotencyKey) {}

  public record CloseInput(@Size(max = 500) String reason, LocalDate closeDate) {}

  public record LineView(
      Long id,
      Long productId,
      String productName,
      Long variantId,
      String variantName,
      int quantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal unitPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal taxRate,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount) {}

  public record View(
      Long id,
      String number,
      Long customerId,
      String customerName,
      Long planId,
      String planName,
      String billingPeriod,
      boolean pausingAllowed,
      boolean renewalAllowed,
      boolean earlyClosingAllowed,
      boolean automaticClosing,
      LocalDate startDate,
      LocalDate expiryDate,
      LocalDate nextBillingDate,
      LocalDate lastBilledDate,
      String paymentTerms,
      SubscriptionState state,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
      Instant pausedAt,
      String pausedBy,
      LocalDate pausedDate,
      Instant resumedAt,
      String resumedBy,
      int totalPausedDays,
      Instant closedAt,
      String closedBy,
      String closureReason,
      Instant renewedAt,
      String renewedBy,
      int renewalCount,
      Long quotationTemplateId,
      Long quotationId,
      LocalDate quotationValidUntil,
      Instant convertedFromQuotationAt,
      List<LineView> lines,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Subscription s) {
    var plan =
        s.plan == null
            ? null
            : org.hibernate.Hibernate.unproxy(s.plan, com.brightloop.modules.plan.Plan.class);
    var customer =
        s.customer == null
            ? null
            : org.hibernate.Hibernate.unproxy(
                s.customer, com.brightloop.modules.customer.Customer.class);
    return new View(
        s.id,
        s.number,
        s.customer.getId(),
        customer != null ? customer.name : s.customer.getName(),
        s.plan.getId(),
        s.planName,
        plan != null && plan.billingPeriod != null ? plan.billingPeriod.name() : null,
        plan != null && plan.pausingAllowed,
        plan != null && plan.renewalAllowed,
        plan != null && plan.earlyClosingAllowed,
        plan != null && plan.automaticClosing,
        s.startDate,
        s.expiryDate,
        s.nextBillingDate,
        s.lastBilledDate,
        s.paymentTerms,
        s.state,
        s.total,
        s.pausedAt,
        s.pausedBy,
        s.pausedDate,
        s.resumedAt,
        s.resumedBy,
        s.totalPausedDays,
        s.closedAt,
        s.closedBy,
        s.closureReason,
        s.renewedAt,
        s.renewedBy,
        s.renewalCount,
        s.quotationTemplateId,
        s.quotationId,
        s.quotationValidUntil,
        s.convertedFromQuotationAt,
        s.lines.stream()
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
                        l.taxRate,
                        l.amount))
            .toList(),
        s.createdAt,
        s.updatedAt);
  }
}

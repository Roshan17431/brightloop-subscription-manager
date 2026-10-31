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
      LocalDate startDate,
      LocalDate expiryDate,
      LocalDate nextBillingDate,
      LocalDate lastBilledDate,
      String paymentTerms,
      SubscriptionState state,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total,
      List<LineView> lines,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Subscription s) {
    var plan =
        s.plan == null
            ? null
            : org.hibernate.Hibernate.unproxy(s.plan, com.brightloop.modules.plan.Plan.class);
    return new View(
        s.id,
        s.number,
        s.customer.getId(),
        s.customer.getName(),
        s.plan.getId(),
        s.planName,
        plan != null && plan.billingPeriod != null ? plan.billingPeriod.name() : null,
        s.startDate,
        s.expiryDate,
        s.nextBillingDate,
        s.lastBilledDate,
        s.paymentTerms,
        s.state,
        s.total,
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

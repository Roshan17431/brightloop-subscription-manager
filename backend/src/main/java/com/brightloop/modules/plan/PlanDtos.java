package com.brightloop.modules.plan;

import com.brightloop.config.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.List;

public final class PlanDtos {
  private PlanDtos() {}

  public record Input(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 4000) String description,
      @NotNull BillingPeriod billingPeriod,
      @NotNull
          @DecimalMin("0.00")
          @Digits(integer = 10, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal price,
      @Min(1) @Max(1000000) int minimumQuantity,
      LocalDate availableFrom,
      LocalDate availableUntil,
      boolean automaticClosing,
      boolean earlyClosingAllowed,
      boolean pausingAllowed,
      boolean renewalAllowed,
      AccountStatus status,
      @NotNull @Size(min = 1, max = 100) List<@NotNull Long> productIds) {}

  public record View(
      Long id,
      String name,
      String description,
      BillingPeriod billingPeriod,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal price,
      int minimumQuantity,
      LocalDate availableFrom,
      LocalDate availableUntil,
      boolean automaticClosing,
      boolean earlyClosingAllowed,
      boolean pausingAllowed,
      boolean renewalAllowed,
      AccountStatus status,
      List<Long> productIds,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Plan p) {
    return new View(
        p.id,
        p.name,
        p.description,
        p.billingPeriod,
        p.price,
        p.minimumQuantity,
        p.availableFrom,
        p.availableUntil,
        p.automaticClosing,
        p.earlyClosingAllowed,
        p.pausingAllowed,
        p.renewalAllowed,
        p.status,
        p.products.stream().map(product -> product.getId()).sorted().toList(),
        p.createdAt,
        p.updatedAt);
  }
}

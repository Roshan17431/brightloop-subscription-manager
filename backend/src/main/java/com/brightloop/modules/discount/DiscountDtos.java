package com.brightloop.modules.discount;

import com.brightloop.config.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class DiscountDtos {
  private DiscountDtos() {}

  public record Input(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 64) String code,
      @Size(max = 1000) String description,
      @NotNull DiscountType type,
      @NotNull
          @DecimalMin("0.01")
          @Digits(integer = 12, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal value,
      DiscountScope scope,
      Long productId,
      Long subscriptionId,
      @Min(1) @Max(1000000) Integer minimumQuantity,
      @DecimalMin("0.00")
          @Digits(integer = 12, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal minimumPurchase,
      LocalDate validFrom,
      LocalDate validUntil,
      @Min(1) @Max(10000000) Integer usageLimit,
      AccountStatus status) {}

  public record View(
      Long id,
      String name,
      String code,
      String description,
      DiscountType type,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal value,
      DiscountScope scope,
      Long productId,
      String productName,
      Long subscriptionId,
      String subscriptionNumber,
      Integer minimumQuantity,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal minimumPurchase,
      LocalDate validFrom,
      LocalDate validUntil,
      Integer usageLimit,
      int usedCount,
      AccountStatus status,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Discount d) {
    var prod =
        d.product == null
            ? null
            : org.hibernate.Hibernate.unproxy(
                d.product, com.brightloop.modules.product.Product.class);
    var sub =
        d.subscription == null
            ? null
            : org.hibernate.Hibernate.unproxy(
                d.subscription, com.brightloop.modules.subscription.Subscription.class);
    return new View(
        d.id,
        d.name,
        d.code,
        d.description,
        d.type,
        d.value,
        d.scope,
        prod == null ? null : prod.getId(),
        prod == null ? null : prod.name,
        sub == null ? null : sub.getId(),
        sub == null ? null : sub.number,
        d.minimumQuantity,
        d.minimumPurchase,
        d.validFrom,
        d.validUntil,
        d.usageLimit,
        d.usedCount,
        d.status,
        d.createdAt,
        d.updatedAt);
  }
}

package com.brightloop.modules.tax;

import com.brightloop.config.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class TaxDtos {
  private TaxDtos() {}

  public record Input(
      @NotBlank @Size(max = 160) String name,
      @Size(max = 1000) String description,
      @NotNull
          @DecimalMin("0.00")
          @DecimalMax("100.00")
          @Digits(integer = 3, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal rate,
      @Size(max = 80) String productType,
      Long productId,
      boolean isDefault,
      LocalDate effectiveFrom,
      LocalDate effectiveUntil,
      AccountStatus status) {}

  public record View(
      Long id,
      String name,
      String description,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal rate,
      String productType,
      Long productId,
      String productName,
      boolean isDefault,
      LocalDate effectiveFrom,
      LocalDate effectiveUntil,
      AccountStatus status,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(TaxRule r) {
    var prod =
        r.product == null
            ? null
            : org.hibernate.Hibernate.unproxy(
                r.product, com.brightloop.modules.product.Product.class);
    return new View(
        r.id,
        r.name,
        r.description,
        r.rate,
        r.productType,
        prod == null ? null : prod.getId(),
        prod == null ? null : prod.name,
        r.isDefault,
        r.effectiveFrom,
        r.effectiveUntil,
        r.status,
        r.createdAt,
        r.updatedAt);
  }
}

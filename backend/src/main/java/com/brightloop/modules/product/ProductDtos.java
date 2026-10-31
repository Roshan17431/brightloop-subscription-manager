package com.brightloop.modules.product;

import com.brightloop.config.AccountStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ProductDtos {
  private ProductDtos() {}

  public record VariantInput(
      Long id,
      @NotBlank @Size(max = 120) String name,
      @NotNull @Digits(integer = 10, fraction = 2) @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal priceAdjustment) {}

  public record Input(
      @NotBlank @Size(max = 160) String name,
      @NotBlank @Size(max = 80) String type,
      @NotNull
          @DecimalMin("0.00")
          @Digits(integer = 10, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal salesPrice,
      @NotNull
          @DecimalMin("0.00")
          @Digits(integer = 10, fraction = 2)
          @JsonFormat(shape = JsonFormat.Shape.STRING)
          BigDecimal costPrice,
      @Size(max = 4000) String notes,
      AccountStatus status,
      @NotNull @Size(max = 100) List<@NotNull @Valid VariantInput> variants) {}

  public record VariantView(
      Long id,
      String name,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal priceAdjustment) {}

  public record View(
      Long id,
      String name,
      String type,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal salesPrice,
      @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal costPrice,
      String notes,
      AccountStatus status,
      Instant createdAt,
      Instant updatedAt,
      List<VariantView> variants) {}

  public static View view(Product p) {
    return view(p, true);
  }

  public static View view(Product p, boolean includeCost) {
    return new View(
        p.id,
        p.name,
        p.type,
        p.salesPrice,
        includeCost ? p.costPrice : null,
        p.notes,
        p.status,
        p.createdAt,
        p.updatedAt,
        p.variants.stream().map(v -> new VariantView(v.id, v.name, v.priceAdjustment)).toList());
  }
}

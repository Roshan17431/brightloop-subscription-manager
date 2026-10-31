package com.brightloop.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.brightloop.config.AccountStatus;
import com.brightloop.modules.product.ProductDtos;
import com.brightloop.modules.subscription.SubscriptionDtos;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CollectionValidationTest {
  @Test
  void rejectsNullProductVariantElements() {
    var input =
        new ProductDtos.Input(
            "Product",
            "SERVICE",
            BigDecimal.ONE,
            BigDecimal.ZERO,
            null,
            AccountStatus.ACTIVE,
            Collections.singletonList(null));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertTrue(
          factory.getValidator().validate(input).stream()
              .anyMatch(v -> v.getPropertyPath().toString().startsWith("variants")));
    }
  }

  @Test
  void rejectsNullSubscriptionLineElements() {
    var input =
        new SubscriptionDtos.Input(
            1L,
            1L,
            LocalDate.now(),
            LocalDate.now().plusDays(30),
            "Net 30",
            Collections.singletonList(null));
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertTrue(
          factory.getValidator().validate(input).stream()
              .anyMatch(v -> v.getPropertyPath().toString().startsWith("lines")));
    }
  }
}

package com.brightloop.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class RulesTest {
  @Test
  void passwordRequiresMoreThanEightUpperLowerAndSpecial() {
    assertDoesNotThrow(() -> Rules.password("Validpass!"));
    for (String value :
        new String[] {"shortA!", "lowercase!", "UPPERCASE!", "NoSpecial123", "EightAA!"})
      assertThrows(IllegalArgumentException.class, () -> Rules.password(value));
  }

  @Test
  void variantAdjustmentPreservesBaseAndUsesDecimalArithmetic() {
    var base = new BigDecimal("1000.00");
    assertEquals(new BigDecimal("1560.00"), Rules.unitPrice(base, new BigDecimal("560.00")));
    assertEquals(new BigDecimal("1000.00"), base);
    assertThrows(
        IllegalArgumentException.class, () -> Rules.unitPrice(base, new BigDecimal("-1001")));
  }

  @Test
  void lineIncludesTaxAndRoundsOnlyFinalAmount() {
    assertEquals(
        new BigDecimal("3539.99"),
        Rules.lineAmount(new BigDecimal("999.997"), 3, new BigDecimal("18")));
    assertThrows(
        IllegalArgumentException.class, () -> Rules.lineAmount(BigDecimal.ONE, 0, BigDecimal.ZERO));
  }

  @Test
  void lifecycleAllowsOnlySequentialMoves() {
    assertDoesNotThrow(() -> Rules.transition("DRAFT", "QUOTATION"));
    assertDoesNotThrow(() -> Rules.transition("QUOTATION", "CONFIRMED"));
    assertDoesNotThrow(() -> Rules.transition("CONFIRMED", "ACTIVE"));
    assertDoesNotThrow(() -> Rules.transition("ACTIVE", "CLOSED"));
    assertThrows(IllegalArgumentException.class, () -> Rules.transition("DRAFT", "ACTIVE"));
    assertThrows(IllegalArgumentException.class, () -> Rules.transition("CLOSED", "DRAFT"));
  }

  @Test
  void rejectsReversedDates() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Rules.dates(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1)));
  }
}

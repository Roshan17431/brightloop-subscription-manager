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

  @Test
  void advancesBillingDatesAcrossAllCyclesAndEndOfMonth() {
    var jan31 = LocalDate.of(2026, 1, 31);
    assertEquals(
        LocalDate.of(2026, 2, 1),
        Rules.advanceBillingDate(jan31, com.brightloop.modules.plan.BillingPeriod.DAILY, jan31));
    assertEquals(
        LocalDate.of(2026, 2, 7),
        Rules.advanceBillingDate(jan31, com.brightloop.modules.plan.BillingPeriod.WEEKLY, jan31));

    var feb28 =
        Rules.advanceBillingDate(jan31, com.brightloop.modules.plan.BillingPeriod.MONTHLY, jan31);
    assertEquals(LocalDate.of(2026, 2, 28), feb28);
    var mar31 =
        Rules.advanceBillingDate(feb28, com.brightloop.modules.plan.BillingPeriod.MONTHLY, jan31);
    assertEquals(LocalDate.of(2026, 3, 31), mar31);
    var apr30 =
        Rules.advanceBillingDate(mar31, com.brightloop.modules.plan.BillingPeriod.MONTHLY, jan31);
    assertEquals(LocalDate.of(2026, 4, 30), apr30);

    var leapDay = LocalDate.of(2024, 2, 29);
    var nextYear =
        Rules.advanceBillingDate(
            leapDay, com.brightloop.modules.plan.BillingPeriod.YEARLY, leapDay);
    assertEquals(LocalDate.of(2025, 2, 28), nextYear);
  }

  @Test
  void calculatesSubtotalDiscountAndTaxAccurately() {
    var sub = Rules.lineSubtotal(new BigDecimal("1560.00"), 2);
    assertEquals(new BigDecimal("3120.00"), sub);

    var pctDisc = Rules.discountAmount(sub, "PERCENTAGE", new BigDecimal("15.00"));
    assertEquals(new BigDecimal("468.00"), pctDisc);

    var fixedDisc = Rules.discountAmount(sub, "FIXED", new BigDecimal("500.00"));
    assertEquals(new BigDecimal("500.00"), fixedDisc);

    var cappedDisc = Rules.discountAmount(sub, "FIXED", new BigDecimal("5000.00"));
    assertEquals(new BigDecimal("3120.00"), cappedDisc);

    var taxable = sub.subtract(pctDisc);
    var tax = Rules.taxAmount(taxable, new BigDecimal("18.00"));
    assertEquals(new BigDecimal("477.36"), tax);
    assertEquals(new BigDecimal("3129.36"), taxable.add(tax));
  }

  @Test
  void enforcesInvoiceLifecycleAndPaidEvidenceRule() {
    assertDoesNotThrow(() -> Rules.invoiceTransition("DRAFT", "CONFIRMED", false));
    assertDoesNotThrow(() -> Rules.invoiceTransition("DRAFT", "CANCELLED", false));
    assertDoesNotThrow(() -> Rules.invoiceTransition("CONFIRMED", "CANCELLED", false));
    assertThrows(
        IllegalArgumentException.class, () -> Rules.invoiceTransition("DRAFT", "PAID", false));
    assertThrows(
        IllegalArgumentException.class, () -> Rules.invoiceTransition("CONFIRMED", "PAID", false));
    assertDoesNotThrow(() -> Rules.invoiceTransition("CONFIRMED", "PAID", true));
    assertThrows(
        IllegalArgumentException.class,
        () -> Rules.invoiceTransition("CANCELLED", "CONFIRMED", false));
  }
}

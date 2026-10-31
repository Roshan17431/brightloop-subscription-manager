package com.brightloop.validation;

import com.brightloop.modules.plan.BillingPeriod;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class Rules {
  private Rules() {}

  public static void password(String value) {
    if (value == null
        || value.length() < 9
        || value.getBytes(StandardCharsets.UTF_8).length > 72
        || !value.matches("(?s).*[A-Z].*")
        || !value.matches("(?s).*[a-z].*")
        || !value.matches("(?s).*[^A-Za-z0-9\\s].*"))
      throw new IllegalArgumentException(
          "Password must contain 9 or more characters, uppercase, lowercase and a special"
              + " character, and at most 72 UTF-8 bytes");
  }

  public static BigDecimal unitPrice(BigDecimal base, BigDecimal adjustment) {
    var result = base.add(adjustment == null ? BigDecimal.ZERO : adjustment);
    if (base.signum() < 0 || result.signum() < 0)
      throw new IllegalArgumentException("Product unit price cannot be negative");
    return result.setScale(2, RoundingMode.UNNECESSARY);
  }

  public static BigDecimal lineAmount(BigDecimal unit, int quantity, BigDecimal tax) {
    if (unit.signum() < 0
        || quantity < 1
        || tax.signum() < 0
        || tax.compareTo(new BigDecimal("100")) > 0)
      throw new IllegalArgumentException("Invalid quantity, price or tax rate");
    return unit.multiply(BigDecimal.valueOf(quantity))
        .multiply(BigDecimal.ONE.add(tax.movePointLeft(2)))
        .setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal lineSubtotal(BigDecimal unit, int quantity) {
    if (unit == null || unit.signum() < 0 || quantity < 1)
      throw new IllegalArgumentException("Invalid quantity or unit price");
    return unit.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal discountAmount(
      BigDecimal baseAmount, String discountType, BigDecimal discountValue) {
    if (baseAmount == null || baseAmount.signum() < 0)
      throw new IllegalArgumentException("Base amount cannot be negative");
    if (discountValue == null || discountValue.signum() <= 0)
      throw new IllegalArgumentException("Discount value must be positive");
    if ("PERCENTAGE".equals(discountType)) {
      if (discountValue.compareTo(new BigDecimal("100")) > 0)
        throw new IllegalArgumentException("Percentage discount cannot exceed 100");
      var calculated =
          baseAmount.multiply(discountValue.movePointLeft(2)).setScale(2, RoundingMode.HALF_UP);
      return calculated.min(baseAmount);
    }
    if ("FIXED".equals(discountType)) {
      return discountValue.setScale(2, RoundingMode.HALF_UP).min(baseAmount);
    }
    throw new IllegalArgumentException("Unsupported discount type: " + discountType);
  }

  public static BigDecimal taxAmount(BigDecimal taxableAmount, BigDecimal taxRate) {
    if (taxableAmount == null
        || taxableAmount.signum() < 0
        || taxRate == null
        || taxRate.signum() < 0
        || taxRate.compareTo(new BigDecimal("100")) > 0)
      throw new IllegalArgumentException("Invalid taxable amount or tax rate");
    return taxableAmount.multiply(taxRate.movePointLeft(2)).setScale(2, RoundingMode.HALF_UP);
  }

  public static LocalDate advanceBillingDate(LocalDate currentDate, BillingPeriod period) {
    return advanceBillingDate(currentDate, period, currentDate);
  }

  public static LocalDate advanceBillingDate(
      LocalDate currentDate, BillingPeriod period, LocalDate anchorDate) {
    if (currentDate == null || period == null)
      throw new IllegalArgumentException("Billing date and period are required");
    int anchorDay =
        anchorDate != null ? anchorDate.getDayOfMonth() : currentDate.getDayOfMonth();
    return switch (period) {
      case DAILY -> currentDate.plusDays(1);
      case WEEKLY -> currentDate.plusWeeks(1);
      case MONTHLY -> {
        var nextYm = YearMonth.from(currentDate).plusMonths(1);
        yield nextYm.atDay(Math.min(anchorDay, nextYm.lengthOfMonth()));
      }
      case YEARLY -> {
        var nextYm = YearMonth.from(currentDate).plusYears(1);
        yield nextYm.atDay(Math.min(anchorDay, nextYm.lengthOfMonth()));
      }
    };
  }

  public static LocalDate billingPeriodEnd(
      LocalDate periodStart, BillingPeriod period, LocalDate anchorDate, LocalDate expiryDate) {
    var nextStart = advanceBillingDate(periodStart, period, anchorDate);
    var naturalEnd = nextStart.minusDays(1);
    if (naturalEnd.isBefore(periodStart)) naturalEnd = periodStart;
    if (expiryDate != null && !expiryDate.isBefore(periodStart) && expiryDate.isBefore(naturalEnd))
      return expiryDate;
    return naturalEnd;
  }

  public static void transition(String current, String target) {
    var states = List.of("DRAFT", "QUOTATION", "CONFIRMED", "ACTIVE", "CLOSED");
    int index = states.indexOf(current);
    if (index < 0 || index == states.size() - 1 || !states.get(index + 1).equals(target))
      throw new IllegalArgumentException("Only the next sequential subscription state is allowed");
  }

  public static void invoiceTransition(
      String current, String target, boolean hasPaymentEvidence) {
    if (current == null || target == null || current.equals(target))
      throw new IllegalArgumentException("Invalid invoice state transition");
    switch (current) {
      case "DRAFT" -> {
        if ("PAID".equals(target))
          throw new IllegalArgumentException(
              "Invoice must be confirmed before it can be paid, and requires recorded payment evidence");
        if (!"CONFIRMED".equals(target) && !"CANCELLED".equals(target))
          throw new IllegalArgumentException("Draft invoice can only move to CONFIRMED or CANCELLED");
      }
      case "CONFIRMED" -> {
        if ("PAID".equals(target)) {
          if (!hasPaymentEvidence)
            throw new IllegalArgumentException(
                "Cannot mark invoice as PAID without recorded payment evidence; full payment"
                    + " processing belongs to Phase 3");
        } else if (!"CANCELLED".equals(target)) {
          throw new IllegalArgumentException(
              "Confirmed invoice can only move to PAID (with payment evidence) or CANCELLED");
        }
      }
      default ->
          throw new IllegalArgumentException(
              "Invoice in state " + current + " cannot be transitioned to " + target);
    }
  }

  public static void dates(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start))
      throw new IllegalArgumentException("End date must be on or after start date");
  }
}

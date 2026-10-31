package com.brightloop.validation;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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

  public static void transition(String current, String target) {
    var states = List.of("DRAFT", "QUOTATION", "CONFIRMED", "ACTIVE", "CLOSED");
    int index = states.indexOf(current);
    if (index < 0 || index == states.size() - 1 || !states.get(index + 1).equals(target))
      throw new IllegalArgumentException("Only the next sequential subscription state is allowed");
  }

  public static void dates(LocalDate start, LocalDate end) {
    if (start != null && end != null && end.isBefore(start))
      throw new IllegalArgumentException("End date must be on or after start date");
  }
}

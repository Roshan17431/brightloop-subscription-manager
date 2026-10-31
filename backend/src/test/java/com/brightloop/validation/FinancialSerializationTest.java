package com.brightloop.validation;

import static org.junit.jupiter.api.Assertions.*;

import com.brightloop.modules.subscription.SubscriptionDtos;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FinancialSerializationTest {
  @Test
  void serializesFinancialSnapshotsAsExactDecimalStrings() throws Exception {
    var line =
        new SubscriptionDtos.LineView(
            1L,
            1L,
            "Product",
            null,
            null,
            999999,
            new BigDecimal("9999999999.99"),
            new BigDecimal("0.00"),
            new BigDecimal("9999989999990000.01"));
    var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(line));
    assertTrue(json.path("unitPrice").isTextual());
    assertEquals("9999999999.99", json.path("unitPrice").asText());
    assertTrue(json.path("amount").isTextual());
    assertEquals("9999989999990000.01", json.path("amount").asText());
  }
}

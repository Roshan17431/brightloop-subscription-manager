package com.brightloop.modules.customer;

import com.brightloop.config.AccountStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class CustomerDtos {
  private CustomerDtos() {}

  public record Input(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @Size(max = 40) String phone,
      @Size(max = 160) String companyName,
      @Size(max = 2000) String billingAddress,
      @NotNull AccountStatus status) {}

  public record View(
      Long id,
      Long userId,
      String name,
      String email,
      String phone,
      String companyName,
      String billingAddress,
      AccountStatus status,
      Instant createdAt,
      Instant updatedAt) {}

  public static View view(Customer c) {
    return new View(
        c.id,
        c.user == null ? null : c.user.getId(),
        c.name,
        c.email,
        c.phone,
        c.companyName,
        c.billingAddress,
        c.status,
        c.createdAt,
        c.updatedAt);
  }
}

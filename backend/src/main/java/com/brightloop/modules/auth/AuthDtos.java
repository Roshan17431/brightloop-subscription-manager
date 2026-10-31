package com.brightloop.modules.auth;

import jakarta.validation.constraints.*;

public final class AuthDtos {
  private AuthDtos() {}

  public record Register(
      @NotBlank @Size(max = 120) String name,
      @NotBlank @Email @Size(max = 254) String email,
      @NotBlank String password) {}

  public record Login(
      @NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 100) String password) {}

  public record Forgot(@NotBlank @Email @Size(max = 254) String email) {}

  public record Reset(@NotBlank @Size(max = 200) String token, @NotBlank String password) {}
}

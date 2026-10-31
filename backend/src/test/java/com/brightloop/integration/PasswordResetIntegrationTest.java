package com.brightloop.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.brightloop.exception.ApiException;
import com.brightloop.modules.auth.*;
import com.brightloop.modules.user.*;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Uses real PostgreSQL row locks and password hashing, without exposing reset secrets. */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PasswordResetIntegrationTest {
  private static org.springframework.boot.web.server.servlet.context
          .ServletWebServerApplicationContext
      application;

  @BeforeAll
  static void startApplication() {
    application = NativeSpringApplication.start();
    service = application.getBean(UserService.class);
    users = application.getBean(UserRepository.class);
    tokens = application.getBean(PasswordResetRepository.class);
    auth = application.getBean(AuthService.class);
    passwords = application.getBean(PasswordEncoder.class);
  }

  @AfterAll
  static void stopApplication() {
    if (application != null) application.close();
  }

  static UserService service;
  static UserRepository users;
  static PasswordResetRepository tokens;
  static AuthService auth;
  static PasswordEncoder passwords;

  private record Fixture(Long userId, String token, String oldPassword) {}

  private Fixture token(Instant expiry, boolean used) {
    String oldPassword = UUID.randomUUID() + "Aa!";
    var user =
        service.register(
            new AuthDtos.Register(
                "Reset verification", "reset-" + UUID.randomUUID() + "@example.test", oldPassword));
    String raw = UUID.randomUUID().toString();
    var token = new PasswordResetToken();
    token.user = users.findById(user.id()).orElseThrow();
    token.tokenHash = AuthService.hash(raw);
    token.expiresAt = expiry;
    token.usedAt = used ? Instant.now().minusSeconds(1) : null;
    tokens.saveAndFlush(token);
    return new Fixture(user.id(), raw, oldPassword);
  }

  @Test
  void expiredResetCannotChangeThePassword() {
    var fixture = token(Instant.now().minusSeconds(1), false);
    assertEquals(
        409,
        assertThrows(
                ApiException.class, () -> auth.reset(fixture.token(), UUID.randomUUID() + "Aa!"))
            .status
            .value());
    assertTrue(
        passwords.matches(
            fixture.oldPassword(), users.findById(fixture.userId()).orElseThrow().passwordHash));
  }

  @Test
  void consumedResetCannotChangeThePassword() {
    var fixture = token(Instant.now().plusSeconds(600), true);
    assertEquals(
        409,
        assertThrows(
                ApiException.class, () -> auth.reset(fixture.token(), UUID.randomUUID() + "Aa!"))
            .status
            .value());
    assertTrue(
        passwords.matches(
            fixture.oldPassword(), users.findById(fixture.userId()).orElseThrow().passwordHash));
  }

  @Test
  void concurrentConsumptionHasExactlyOneWinner() throws Exception {
    var fixture = token(Instant.now().plusSeconds(600), false);
    var ready = new CountDownLatch(2);
    var start = new CountDownLatch(1);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      Callable<Boolean> consume =
          () -> {
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try {
              auth.reset(fixture.token(), UUID.randomUUID() + "Aa!");
              return true;
            } catch (ApiException conflict) {
              assertEquals(409, conflict.status.value());
              return false;
            }
          };
      var one = executor.submit(consume);
      var two = executor.submit(consume);
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
      assertFalse(
          passwords.matches(
              fixture.oldPassword(), users.findById(fixture.userId()).orElseThrow().passwordHash));
    }
  }
}

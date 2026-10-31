package com.brightloop.modules.auth;

import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.user.*;
import com.brightloop.security.SessionRevoker;
import com.brightloop.validation.Rules;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
  private static final Logger log = LoggerFactory.getLogger(AuthService.class);
  private final UserRepository users;
  private final PasswordResetRepository tokens;
  private final PasswordEncoder encoder;
  private final JavaMailSender mail;
  private final SessionRevoker sessions;
  private final AuditService audit;
  private final String frontend;
  private final String from;

  public AuthService(
      UserRepository users,
      PasswordResetRepository tokens,
      PasswordEncoder encoder,
      JavaMailSender mail,
      SessionRevoker sessions,
      AuditService audit,
      @Value("${app.frontend-url}") String frontend,
      @Value("${app.mail-from}") String from) {
    this.users = users;
    this.tokens = tokens;
    this.encoder = encoder;
    this.mail = mail;
    this.sessions = sessions;
    this.audit = audit;
    this.frontend = frontend;
    this.from = from;
  }

  @Transactional
  public void forgot(String email) {
    var user = users.findByEmail(UserService.email(email));
    if (user.isEmpty()) return;
    byte[] random = new byte[32];
    new SecureRandom().nextBytes(random);
    var raw = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    tokens.invalidate(user.get().id, Instant.now());
    var token = new PasswordResetToken();
    token.user = user.get();
    token.tokenHash = hash(raw);
    token.expiresAt = Instant.now().plus(Duration.ofMinutes(30));
    tokens.saveAndFlush(token);
    var message = new SimpleMailMessage();
    message.setFrom(from);
    message.setTo(user.get().email);
    message.setSubject("Reset your Brightloop password");
    message.setText(
        "Reset your password using this single-use link, valid for 30 minutes:\n"
            + frontend
            + "/reset-password?token="
            + raw
            + "\nIf you did not request this, ignore this email.");
    try {
      mail.send(message);
    } catch (MailException e) {
      token.usedAt = Instant.now();
      log.warn("Password reset email delivery failed; verify SMTP configuration");
    }
  }

  @Transactional
  public void reset(String raw, String password) {
    Rules.password(password);
    var token =
        tokens
            .lockByHash(hash(raw))
            .orElseThrow(() -> ApiException.conflict("Reset link is invalid or expired"));
    if (token.usedAt != null || !token.expiresAt.isAfter(Instant.now()))
      throw ApiException.conflict("Reset link is invalid or expired");
    token.usedAt = Instant.now();
    token.user.passwordHash = encoder.encode(password);
    tokens.invalidate(token.user.id, Instant.now());
    sessions.revoke(token.user.email);
    audit.record("PASSWORD_RESET", "USER", token.user.id, "sessions revoked");
  }

  public static String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}

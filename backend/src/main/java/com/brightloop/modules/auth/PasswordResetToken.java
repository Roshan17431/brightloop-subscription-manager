package com.brightloop.modules.auth;

import com.brightloop.modules.user.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  public UserAccount user;

  @Column(nullable = false, unique = true, length = 64)
  public String tokenHash;

  @Column(nullable = false)
  public Instant expiresAt;

  public Instant usedAt;
}

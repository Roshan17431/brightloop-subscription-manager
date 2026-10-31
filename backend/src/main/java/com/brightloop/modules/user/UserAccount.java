package com.brightloop.modules.user;

import com.brightloop.config.RecordEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class UserAccount extends RecordEntity {
  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false, unique = true, length = 254)
  public String email;

  @Column(nullable = false, length = 100)
  public String passwordHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public Role role;

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public void setName(String name) {
    this.name = name;
  }
}

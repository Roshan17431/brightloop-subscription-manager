package com.brightloop.modules.customer;

import com.brightloop.config.*;
import com.brightloop.modules.user.UserAccount;
import jakarta.persistence.*;

@Entity
@Table(name = "customers")
public class Customer extends RecordEntity {
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", unique = true)
  public UserAccount user;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false, unique = true, length = 254)
  public String email;

  @Column(length = 40)
  public String phone;

  @Column(length = 160)
  public String companyName;

  @Column(length = 2000)
  public String billingAddress;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public AccountStatus status = AccountStatus.ACTIVE;

  public String getName() {
    return name;
  }

  public UserAccount getUser() {
    return user;
  }
}

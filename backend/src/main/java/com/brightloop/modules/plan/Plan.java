package com.brightloop.modules.plan;

import com.brightloop.config.*;
import com.brightloop.modules.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "plans")
public class Plan extends RecordEntity {
  @Column(nullable = false, length = 160)
  public String name;

  @Column(length = 4000)
  public String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public BillingPeriod billingPeriod;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal price;

  @Column(nullable = false)
  public int minimumQuantity;

  public LocalDate availableFrom;
  public LocalDate availableUntil;

  @Column(nullable = false)
  public boolean automaticClosing;

  @Column(nullable = false)
  public boolean earlyClosingAllowed;

  @Column(nullable = false)
  public boolean pausingAllowed;

  @Column(nullable = false)
  public boolean renewalAllowed;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public AccountStatus status = AccountStatus.ACTIVE;

  @ManyToMany
  @JoinTable(
      name = "plan_products",
      joinColumns = @JoinColumn(name = "plan_id"),
      inverseJoinColumns = @JoinColumn(name = "product_id"))
  public Set<Product> products = new LinkedHashSet<>();
}

package com.brightloop.modules.subscription;

import com.brightloop.config.RecordEntity;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.plan.Plan;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Entity
@Table(name = "subscriptions")
public class Subscription extends RecordEntity {
  @Version public long version;

  @Column(nullable = false, unique = true, length = 50)
  public String number;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  public Customer customer;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  public Plan plan;

  @Column(nullable = false, length = 160)
  public String planName;

  @Column(nullable = false)
  public LocalDate startDate;

  @Column(nullable = false)
  public LocalDate expiryDate;

  @Column(nullable = false, length = 1000)
  public String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public SubscriptionState state = SubscriptionState.DRAFT;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal total;

  @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<SubscriptionLine> lines = new ArrayList<>();
}

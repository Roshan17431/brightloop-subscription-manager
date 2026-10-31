package com.brightloop.modules.subscription;

import com.brightloop.config.RecordEntity;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.plan.Plan;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
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

  @Column(nullable = false)
  public LocalDate nextBillingDate;

  public LocalDate lastBilledDate;

  @Column(nullable = false, length = 1000)
  public String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public SubscriptionState state = SubscriptionState.DRAFT;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal total;

  @Column(name = "paused_at")
  public Instant pausedAt;

  @Column(name = "paused_by", length = 254)
  public String pausedBy;

  @Column(name = "paused_date")
  public LocalDate pausedDate;

  @Column(name = "resumed_at")
  public Instant resumedAt;

  @Column(name = "resumed_by", length = 254)
  public String resumedBy;

  @Column(name = "total_paused_days", nullable = false)
  public int totalPausedDays = 0;

  @Column(name = "closed_at")
  public Instant closedAt;

  @Column(name = "closed_by", length = 254)
  public String closedBy;

  @Column(name = "closure_reason", length = 500)
  public String closureReason;

  @Column(name = "renewed_at")
  public Instant renewedAt;

  @Column(name = "renewed_by", length = 254)
  public String renewedBy;

  @Column(name = "renewal_count", nullable = false)
  public int renewalCount = 0;

  @Column(name = "last_renewal_key", length = 120)
  public String lastRenewalKey;

  @Column(name = "quotation_template_id")
  public Long quotationTemplateId;

  @Column(name = "quotation_id")
  public Long quotationId;

  @Column(name = "quotation_valid_until")
  public LocalDate quotationValidUntil;

  @Column(name = "converted_from_quotation_at")
  public Instant convertedFromQuotationAt;

  @OneToMany(mappedBy = "subscription", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<SubscriptionLine> lines = new ArrayList<>();
}

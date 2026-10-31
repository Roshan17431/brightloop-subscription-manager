package com.brightloop.modules.quotation;

import com.brightloop.config.RecordEntity;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.plan.Plan;
import com.brightloop.modules.subscription.Subscription;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotations")
public class Quotation extends RecordEntity {
  @Version public long version;

  @Column(name = "quotation_number", nullable = false, unique = true, length = 50)
  public String quotationNumber;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "template_id")
  public QuotationTemplate template;

  @Column(name = "template_name", length = 160)
  public String templateName;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  public Customer customer;

  @Column(name = "customer_name", nullable = false, length = 120)
  public String customerName;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  public Plan plan;

  @Column(name = "plan_name", nullable = false, length = 160)
  public String planName;

  @Column(name = "issue_date", nullable = false)
  public LocalDate issueDate;

  @Column(name = "valid_until", nullable = false)
  public LocalDate validUntil;

  @Column(name = "start_date", nullable = false)
  public LocalDate startDate;

  @Column(name = "expiry_date", nullable = false)
  public LocalDate expiryDate;

  @Column(name = "payment_terms", nullable = false, length = 1000)
  public String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public QuotationState state = QuotationState.SENT;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal subtotal;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
  public BigDecimal taxAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal total;

  @Column(length = 2000)
  public String notes;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "converted_subscription_id", unique = true)
  public Subscription convertedSubscription;

  @Column(name = "converted_subscription_number", length = 50)
  public String convertedSubscriptionNumber;

  @Column(name = "confirmed_at")
  public Instant confirmedAt;

  @Column(name = "converted_at")
  public Instant convertedAt;

  @Column(name = "cancelled_at")
  public Instant cancelledAt;

  @Column(name = "created_by", nullable = false, length = 254)
  public String createdBy;

  @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<QuotationLine> lines = new ArrayList<>();
}

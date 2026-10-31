package com.brightloop.modules.invoice;

import com.brightloop.config.RecordEntity;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.discount.Discount;
import com.brightloop.modules.plan.BillingPeriod;
import com.brightloop.modules.plan.Plan;
import com.brightloop.modules.subscription.Subscription;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
public class Invoice extends RecordEntity {
  @Version public long version;

  @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
  public String invoiceNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  public Subscription subscription;

  @Column(name = "subscription_number", nullable = false, length = 50)
  public String subscriptionNumber;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "customer_id", nullable = false)
  public Customer customer;

  @Column(name = "customer_name", nullable = false, length = 120)
  public String customerName;

  @Column(name = "customer_email", nullable = false, length = 254)
  public String customerEmail;

  @Column(name = "customer_phone", length = 40)
  public String customerPhone;

  @Column(name = "customer_company", length = 160)
  public String customerCompany;

  @Column(name = "billing_address", length = 2000)
  public String billingAddress;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  public Plan plan;

  @Column(name = "plan_name", nullable = false, length = 160)
  public String planName;

  @Enumerated(EnumType.STRING)
  @Column(name = "billing_period", nullable = false, length = 16)
  public BillingPeriod billingPeriod;

  @Column(name = "billing_period_start", nullable = false)
  public LocalDate billingPeriodStart;

  @Column(name = "billing_period_end", nullable = false)
  public LocalDate billingPeriodEnd;

  @Column(name = "issue_date", nullable = false)
  public LocalDate issueDate;

  @Column(name = "due_date", nullable = false)
  public LocalDate dueDate;

  @Column(name = "payment_terms", nullable = false, length = 1000)
  public String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public InvoiceState state = InvoiceState.DRAFT;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal subtotal;

  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
  public BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "discount_id")
  public Discount discount;

  @Column(name = "discount_name", length = 160)
  public String discountName;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
  public BigDecimal taxAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal total;

  @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
  public BigDecimal amountPaid = BigDecimal.ZERO.setScale(2);

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 120)
  public String idempotencyKey;

  @Column(name = "billing_run_id")
  public Long billingRunId;

  @Column(name = "payment_reference", length = 200)
  public String paymentReference;

  @Column(name = "paid_at")
  public Instant paidAt;

  @Column(name = "confirmed_at")
  public Instant confirmedAt;

  @Column(name = "cancelled_at")
  public Instant cancelledAt;

  @Column(name = "sent_at")
  public Instant sentAt;

  @Column(name = "last_followup_at")
  public Instant lastFollowupAt;

  @Column(name = "followup_notes", length = 1000)
  public String followupNotes;

  @Column(name = "followup_by", length = 254)
  public String followupBy;

  @Column(length = 2000)
  public String notes;

  @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<InvoiceLine> lines = new ArrayList<>();
}

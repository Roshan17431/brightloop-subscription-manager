package com.brightloop.modules.quotation;

import com.brightloop.config.AccountStatus;
import com.brightloop.config.RecordEntity;
import com.brightloop.modules.plan.Plan;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quotation_templates")
public class QuotationTemplate extends RecordEntity {
  @Version public long version;

  @Column(nullable = false, length = 160)
  public String name;

  @Column(length = 2000)
  public String description;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "plan_id", nullable = false)
  public Plan plan;

  @Column(name = "plan_name", nullable = false, length = 160)
  public String planName;

  @Column(name = "validity_days", nullable = false)
  public int validityDays;

  @Column(name = "payment_terms", nullable = false, length = 1000)
  public String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public AccountStatus status = AccountStatus.ACTIVE;

  @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<QuotationTemplateLine> lines = new ArrayList<>();
}

package com.brightloop.modules.tax;

import com.brightloop.config.AccountStatus;
import com.brightloop.config.RecordEntity;
import com.brightloop.modules.product.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tax_rules")
public class TaxRule extends RecordEntity {
  @Column(nullable = false, length = 160)
  public String name;

  @Column(length = 1000)
  public String description;

  @Column(nullable = false, precision = 5, scale = 2)
  public BigDecimal rate;

  @Column(name = "product_type", length = 80)
  public String productType;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  public Product product;

  @Column(name = "is_default", nullable = false)
  public boolean isDefault;

  @Column(name = "effective_from")
  public LocalDate effectiveFrom;

  @Column(name = "effective_until")
  public LocalDate effectiveUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public AccountStatus status = AccountStatus.ACTIVE;
}

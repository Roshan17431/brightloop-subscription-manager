package com.brightloop.modules.discount;

import com.brightloop.config.AccountStatus;
import com.brightloop.config.RecordEntity;
import com.brightloop.modules.product.Product;
import com.brightloop.modules.subscription.Subscription;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "discounts")
public class Discount extends RecordEntity {
  @Version public long version;

  @Column(nullable = false, length = 160)
  public String name;

  @Column(length = 64)
  public String code;

  @Column(length = 1000)
  public String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public DiscountType type;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal value;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public DiscountScope scope = DiscountScope.SUBSCRIPTION;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "product_id")
  public Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "subscription_id")
  public Subscription subscription;

  @Column(name = "minimum_quantity")
  public Integer minimumQuantity;

  @Column(name = "minimum_purchase", precision = 14, scale = 2)
  public BigDecimal minimumPurchase;

  @Column(name = "valid_from")
  public LocalDate validFrom;

  @Column(name = "valid_until")
  public LocalDate validUntil;

  @Column(name = "usage_limit")
  public Integer usageLimit;

  @Column(name = "used_count", nullable = false)
  public int usedCount = 0;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public AccountStatus status = AccountStatus.ACTIVE;
}

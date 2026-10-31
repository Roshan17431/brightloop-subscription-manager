package com.brightloop.modules.subscription;

import com.brightloop.modules.product.*;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "subscription_lines")
public class SubscriptionLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "subscription_id", nullable = false)
  public Subscription subscription;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  public Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variant_id")
  public ProductVariant variant;

  @Column(nullable = false, length = 160)
  public String productName;

  @Column(length = 120)
  public String variantName;

  @Column(nullable = false)
  public int quantity;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal unitPrice;

  @Column(nullable = false, precision = 5, scale = 2)
  public BigDecimal taxRate;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal amount;
}

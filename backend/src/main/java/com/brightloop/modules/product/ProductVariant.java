package com.brightloop.modules.product;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
public class ProductVariant {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  public Product product;

  @Column(nullable = false, length = 120)
  public String name;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal priceAdjustment;

  public Long getId() {
    return id;
  }
}

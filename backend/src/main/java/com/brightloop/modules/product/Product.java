package com.brightloop.modules.product;

import com.brightloop.config.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.*;

@Entity
@Table(name = "products")
public class Product extends RecordEntity {
  @Column(nullable = false, length = 160)
  public String name;

  @Column(nullable = false, length = 80)
  public String type;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal salesPrice;

  @Column(nullable = false, precision = 14, scale = 2)
  public BigDecimal costPrice;

  @Column(length = 4000)
  public String notes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  public AccountStatus status = AccountStatus.ACTIVE;

  @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy("id")
  public List<ProductVariant> variants = new ArrayList<>();
}

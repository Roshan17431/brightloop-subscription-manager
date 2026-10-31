package com.brightloop.modules.quotation;

import com.brightloop.modules.product.Product;
import com.brightloop.modules.product.ProductVariant;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "quotation_template_lines")
public class QuotationTemplateLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "template_id", nullable = false)
  public QuotationTemplate template;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false)
  public Product product;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "variant_id")
  public ProductVariant variant;

  @Column(name = "product_name", nullable = false, length = 160)
  public String productName;

  @Column(name = "variant_name", length = 120)
  public String variantName;

  @Column(nullable = false)
  public int quantity;

  @Column(name = "unit_price", nullable = false, precision = 14, scale = 2)
  public BigDecimal unitPrice;

  @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
  public BigDecimal taxRate;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal amount;
}

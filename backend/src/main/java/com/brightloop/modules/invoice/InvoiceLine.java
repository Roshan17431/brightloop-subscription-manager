package com.brightloop.modules.invoice;

import com.brightloop.modules.discount.Discount;
import com.brightloop.modules.product.Product;
import com.brightloop.modules.product.ProductVariant;
import com.brightloop.modules.tax.TaxRule;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "invoice_id", nullable = false)
  public Invoice invoice;

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

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal subtotal;

  @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
  public BigDecimal discountAmount = BigDecimal.ZERO.setScale(2);

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "discount_id")
  public Discount discount;

  @Column(name = "discount_name", length = 160)
  public String discountName;

  @Column(name = "tax_rate", nullable = false, precision = 5, scale = 2)
  public BigDecimal taxRate;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "tax_rule_id")
  public TaxRule taxRule;

  @Column(name = "tax_rule_name", length = 160)
  public String taxRuleName;

  @Column(name = "tax_amount", nullable = false, precision = 19, scale = 2)
  public BigDecimal taxAmount;

  @Column(nullable = false, precision = 19, scale = 2)
  public BigDecimal amount;
}

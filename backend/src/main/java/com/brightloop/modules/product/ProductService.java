package com.brightloop.modules.product;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.user.Role;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {
  private final ProductRepository products;
  private final CurrentUser current;
  private final AuditService audit;

  public ProductService(ProductRepository products, CurrentUser current, AuditService audit) {
    this.products = products;
    this.current = current;
    this.audit = audit;
  }

  public Product find(Long id) {
    return products
        .findById(id)
        .map(value -> org.hibernate.Hibernate.unproxy(value, Product.class))
        .orElseThrow(() -> ApiException.notFound("Product"));
  }

  @Transactional(readOnly = true)
  public PageResponse<ProductDtos.View> list(String q, AccountStatus status, int page, int size) {
    boolean includeCost = current.get().role != Role.CUSTOMER;
    return PageResponse.from(
        products.search(PageResponse.search(q), status, PageResponse.request(page, size)),
        product -> ProductDtos.view(product, includeCost));
  }

  @Transactional(readOnly = true)
  public ProductDtos.View get(Long id) {
    return ProductDtos.view(find(id), current.get().role != Role.CUSTOMER);
  }

  @Transactional
  public ProductDtos.View create(ProductDtos.Input input) {
    current.requireAdmin();
    var product = new Product();
    apply(product, input);
    products.saveAndFlush(product);
    audit.record("PRODUCT_CREATED", "PRODUCT", product.id, "variants=" + product.variants.size());
    return ProductDtos.view(product);
  }

  @Transactional
  public ProductDtos.View update(Long id, ProductDtos.Input input) {
    current.requireAdmin();
    var product = find(id);
    apply(product, input);
    products.saveAndFlush(product);
    audit.record("PRODUCT_UPDATED", "PRODUCT", id, "variants=" + product.variants.size());
    return ProductDtos.view(product);
  }

  @Transactional
  public void delete(Long id) {
    current.requireAdmin();
    var p = find(id);
    products.delete(p);
    products.flush();
    audit.record("PRODUCT_DELETED", "PRODUCT", id, "catalog removal");
  }

  private void apply(Product p, ProductDtos.Input input) {
    p.name = input.name().trim();
    p.type = input.type().trim();
    p.salesPrice = input.salesPrice();
    p.costPrice = input.costPrice();
    p.notes = input.notes();
    p.status = input.status() == null ? AccountStatus.ACTIVE : input.status();
    var ids = new HashSet<Long>();
    var names = new HashSet<String>();
    for (var in : input.variants()) {
      if (!names.add(in.name().trim().toLowerCase(Locale.ROOT)))
        throw new IllegalArgumentException("Variant names must be unique within a product");
      Rules.unitPrice(p.salesPrice, in.priceAdjustment());
      ProductVariant variant;
      if (in.id() == null) {
        variant = new ProductVariant();
        variant.product = p;
        p.variants.add(variant);
      } else {
        if (!ids.add(in.id())) throw new IllegalArgumentException("Duplicate variant identifier");
        variant =
            p.variants.stream()
                .filter(v -> in.id().equals(v.id))
                .findFirst()
                .orElseThrow(
                    () -> new IllegalArgumentException("Variant does not belong to this product"));
      }
      variant.name = in.name().trim();
      variant.priceAdjustment = in.priceAdjustment();
    }
    p.variants.removeIf(v -> v.id != null && !ids.contains(v.id));
  }
}

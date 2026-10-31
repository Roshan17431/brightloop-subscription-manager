package com.brightloop.modules.tax;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.product.Product;
import com.brightloop.modules.product.ProductRepository;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxService {
  private final TaxRepository taxes;
  private final ProductRepository products;
  private final CurrentUser current;
  private final AuditService audit;

  public TaxService(
      TaxRepository taxes, ProductRepository products, CurrentUser current, AuditService audit) {
    this.taxes = taxes;
    this.products = products;
    this.current = current;
    this.audit = audit;
  }

  public TaxRule find(Long id) {
    return taxes
        .findById(id)
        .map(v -> org.hibernate.Hibernate.unproxy(v, TaxRule.class))
        .orElseThrow(() -> ApiException.notFound("Tax rule"));
  }

  @Transactional(readOnly = true)
  public PageResponse<TaxDtos.View> list(String q, AccountStatus status, int page, int size) {
    return PageResponse.from(
        taxes.search(PageResponse.search(q), status, PageResponse.request(page, size)),
        TaxDtos::view);
  }

  @Transactional(readOnly = true)
  public TaxDtos.View get(Long id) {
    return TaxDtos.view(find(id));
  }

  @Transactional
  public TaxDtos.View create(TaxDtos.Input input) {
    current.requireAdmin();
    var rule = new TaxRule();
    apply(rule, input);
    taxes.saveAndFlush(rule);
    audit.record("TAX_RULE_CREATED", "TAX_RULE", rule.id, "name=" + rule.name + ",rate=" + rule.rate);
    return TaxDtos.view(rule);
  }

  @Transactional
  public TaxDtos.View update(Long id, TaxDtos.Input input) {
    current.requireAdmin();
    var rule = find(id);
    apply(rule, input);
    taxes.saveAndFlush(rule);
    audit.record("TAX_RULE_UPDATED", "TAX_RULE", rule.id, "name=" + rule.name + ",rate=" + rule.rate);
    return TaxDtos.view(rule);
  }

  @Transactional
  public void delete(Long id) {
    current.requireAdmin();
    taxes.delete(find(id));
    taxes.flush();
    audit.record("TAX_RULE_DELETED", "TAX_RULE", id, "tax rule deleted");
  }

  @Transactional(readOnly = true)
  public Optional<TaxRule> resolveForProduct(Product product, LocalDate billingDate) {
    List<TaxRule> active =
        taxes.findAllActive().stream()
            .filter(r -> isEffectiveOn(r, billingDate))
            .toList();
    if (active.isEmpty()) return Optional.empty();
    if (product != null && product.getId() != null) {
      var byProduct =
          active.stream()
              .filter(r -> r.product != null && r.product.getId().equals(product.getId()))
              .findFirst();
      if (byProduct.isPresent()) return byProduct;
    }
    if (product != null && product.type != null && !product.type.isBlank()) {
      var byType =
          active.stream()
              .filter(
                  r ->
                      r.product == null
                          && r.productType != null
                          && !r.productType.isBlank()
                          && r.productType.trim().equalsIgnoreCase(product.type.trim()))
              .findFirst();
      if (byType.isPresent()) return byType;
    }
    var defaultRule =
        active.stream()
            .filter(
                r ->
                    r.product == null
                        && (r.productType == null || r.productType.isBlank())
                        && r.isDefault)
            .findFirst();
    if (defaultRule.isPresent()) return defaultRule;
    return active.stream()
        .filter(r -> r.product == null && (r.productType == null || r.productType.isBlank()))
        .findFirst();
  }

  public static boolean isEffectiveOn(TaxRule r, LocalDate date) {
    if (r.status != AccountStatus.ACTIVE) return false;
    if (r.effectiveFrom != null && date.isBefore(r.effectiveFrom)) return false;
    if (r.effectiveUntil != null && date.isAfter(r.effectiveUntil)) return false;
    return true;
  }

  private void apply(TaxRule rule, TaxDtos.Input input) {
    Rules.dates(input.effectiveFrom(), input.effectiveUntil());
    Product product = null;
    if (input.productId() != null) {
      product =
          products
              .findById(input.productId())
              .map(v -> org.hibernate.Hibernate.unproxy(v, Product.class))
              .orElseThrow(() -> ApiException.notFound("Product"));
    }
    rule.name = input.name().trim();
    rule.description = input.description() == null ? null : input.description().trim();
    rule.rate = input.rate().setScale(2, RoundingMode.UNNECESSARY);
    rule.productType =
        input.productType() == null || input.productType().isBlank()
            ? null
            : input.productType().trim();
    rule.product = product;
    rule.isDefault = input.isDefault();
    rule.effectiveFrom = input.effectiveFrom();
    rule.effectiveUntil = input.effectiveUntil();
    rule.status = input.status() == null ? AccountStatus.ACTIVE : input.status();
  }
}

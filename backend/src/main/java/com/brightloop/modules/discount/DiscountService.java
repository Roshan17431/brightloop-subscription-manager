package com.brightloop.modules.discount;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.product.Product;
import com.brightloop.modules.product.ProductRepository;
import com.brightloop.modules.subscription.Subscription;
import com.brightloop.modules.subscription.SubscriptionLine;
import com.brightloop.modules.subscription.SubscriptionRepository;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscountService {
  private final DiscountRepository discounts;
  private final ProductRepository products;
  private final SubscriptionRepository subscriptions;
  private final CurrentUser current;
  private final AuditService audit;

  public DiscountService(
      DiscountRepository discounts,
      ProductRepository products,
      SubscriptionRepository subscriptions,
      CurrentUser current,
      AuditService audit) {
    this.discounts = discounts;
    this.products = products;
    this.subscriptions = subscriptions;
    this.current = current;
    this.audit = audit;
  }

  public Discount find(Long id) {
    return discounts
        .findById(id)
        .map(v -> org.hibernate.Hibernate.unproxy(v, Discount.class))
        .orElseThrow(() -> ApiException.notFound("Discount"));
  }

  @Transactional(readOnly = true)
  public PageResponse<DiscountDtos.View> list(String q, AccountStatus status, int page, int size) {
    current.requireManager();
    return PageResponse.from(
        discounts.search(PageResponse.search(q), status, PageResponse.request(page, size)),
        DiscountDtos::view);
  }

  @Transactional(readOnly = true)
  public DiscountDtos.View get(Long id) {
    current.requireManager();
    return DiscountDtos.view(find(id));
  }

  @Transactional
  public DiscountDtos.View create(DiscountDtos.Input input) {
    current.requireAdmin();
    var d = new Discount();
    apply(d, input);
    discounts.saveAndFlush(d);
    audit.record(
        "DISCOUNT_CREATED", "DISCOUNT", d.id, "name=" + d.name + ",type=" + d.type + ",value=" + d.value);
    return DiscountDtos.view(d);
  }

  @Transactional
  public DiscountDtos.View update(Long id, DiscountDtos.Input input) {
    current.requireAdmin();
    var d =
        discounts
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Discount.class))
            .orElseThrow(() -> ApiException.notFound("Discount"));
    apply(d, input);
    discounts.saveAndFlush(d);
    audit.record(
        "DISCOUNT_UPDATED", "DISCOUNT", d.id, "name=" + d.name + ",type=" + d.type + ",value=" + d.value);
    return DiscountDtos.view(d);
  }

  @Transactional
  public void delete(Long id) {
    current.requireAdmin();
    discounts.delete(find(id));
    discounts.flush();
    audit.record("DISCOUNT_DELETED", "DISCOUNT", id, "discount deleted");
  }

  public record AppliedDiscount(
      Discount discount,
      BigDecimal totalDiscountAmount,
      Map<Long, BigDecimal> lineDiscountsBySubLineId) {}

  /**
   * Evaluates eligible active discounts for the subscription on billingDate, locks the winning
   * discount row with PESSIMISTIC_WRITE to prevent concurrent overuse, increments usedCount, and
   * returns the exact per-line and total discount amounts.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public Optional<AppliedDiscount> evaluateAndConsume(
      Subscription subscription, List<SubscriptionLine> lines, LocalDate billingDate) {
    if (lines.isEmpty()) return Optional.empty();
    int totalQty = lines.stream().mapToInt(l -> l.quantity).sum();
    BigDecimal subSubtotal =
        lines.stream()
            .map(l -> Rules.lineSubtotal(l.unitPrice, l.quantity))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (subSubtotal.signum() <= 0) return Optional.empty();

    List<Discount> candidates =
        discounts.findAllActiveWithCapacity().stream()
            .filter(d -> isEligible(d, subscription, lines, totalQty, subSubtotal, billingDate))
            .sorted(
                Comparator.comparing(
                        (Discount d) -> estimateSavings(d, lines, subSubtotal),
                        Comparator.reverseOrder())
                    .thenComparing(
                        (Discount d) -> d.subscription != null || d.product != null ? 1 : 0,
                        Comparator.reverseOrder())
                    .thenComparing((Discount d) -> d.id, Comparator.reverseOrder()))
            .toList();

    for (Discount candidate : candidates) {
      var lockedOpt =
          discounts
              .lockById(candidate.id)
              .map(v -> org.hibernate.Hibernate.unproxy(v, Discount.class));
      if (lockedOpt.isEmpty()) continue;
      var locked = lockedOpt.get();
      if (!isEligible(locked, subscription, lines, totalQty, subSubtotal, billingDate)) {
        continue;
      }
      var lineDiscounts = allocateLineDiscounts(locked, lines, subSubtotal);
      BigDecimal totalDisc =
          lineDiscounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
      if (totalDisc.signum() <= 0) continue;

      locked.usedCount = locked.usedCount + 1;
      discounts.saveAndFlush(locked);
      audit.record(
          "DISCOUNT_APPLIED",
          "DISCOUNT",
          locked.id,
          "subscription=" + subscription.number + ",usedCount=" + locked.usedCount);
      return Optional.of(new AppliedDiscount(locked, totalDisc, lineDiscounts));
    }
    return Optional.empty();
  }

  public static boolean isEligible(
      Discount d,
      Subscription subscription,
      List<SubscriptionLine> lines,
      int totalQty,
      BigDecimal subSubtotal,
      LocalDate billingDate) {
    if (d.status != AccountStatus.ACTIVE) return false;
    if (d.validFrom != null && billingDate.isBefore(d.validFrom)) return false;
    if (d.validUntil != null && billingDate.isAfter(d.validUntil)) return false;
    if (d.usageLimit != null && d.usedCount >= d.usageLimit) return false;
    if (d.subscription != null && !d.subscription.getId().equals(subscription.getId()))
      return false;

    if (d.scope == DiscountScope.PRODUCT) {
      if (d.product == null) return false;
      var matchingLines =
          lines.stream().filter(l -> l.product.getId().equals(d.product.getId())).toList();
      if (matchingLines.isEmpty()) return false;
      int prodQty = matchingLines.stream().mapToInt(l -> l.quantity).sum();
      BigDecimal prodSubtotal =
          matchingLines.stream()
              .map(l -> Rules.lineSubtotal(l.unitPrice, l.quantity))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      if (d.minimumQuantity != null && prodQty < d.minimumQuantity) return false;
      if (d.minimumPurchase != null && prodSubtotal.compareTo(d.minimumPurchase) < 0) return false;
      return prodSubtotal.signum() > 0;
    } else {
      if (d.product != null
          && lines.stream().noneMatch(l -> l.product.getId().equals(d.product.getId()))) {
        return false;
      }
      if (d.minimumQuantity != null && totalQty < d.minimumQuantity) return false;
      if (d.minimumPurchase != null && subSubtotal.compareTo(d.minimumPurchase) < 0) return false;
      return subSubtotal.signum() > 0;
    }
  }

  private static BigDecimal estimateSavings(
      Discount d, List<SubscriptionLine> lines, BigDecimal subSubtotal) {
    if (d.scope == DiscountScope.PRODUCT && d.product != null) {
      BigDecimal prodSubtotal =
          lines.stream()
              .filter(l -> l.product.getId().equals(d.product.getId()))
              .map(l -> Rules.lineSubtotal(l.unitPrice, l.quantity))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      return Rules.discountAmount(prodSubtotal, d.type.name(), d.value);
    }
    return Rules.discountAmount(subSubtotal, d.type.name(), d.value);
  }

  private static Map<Long, BigDecimal> allocateLineDiscounts(
      Discount d, List<SubscriptionLine> lines, BigDecimal subSubtotal) {
    var result = new LinkedHashMap<Long, BigDecimal>();
    List<SubscriptionLine> targetLines =
        (d.scope == DiscountScope.PRODUCT && d.product != null)
            ? lines.stream().filter(l -> l.product.getId().equals(d.product.getId())).toList()
            : lines;
    BigDecimal targetSubtotal =
        targetLines.stream()
            .map(l -> Rules.lineSubtotal(l.unitPrice, l.quantity))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (targetLines.isEmpty() || targetSubtotal.signum() <= 0) return result;

    BigDecimal totalDiscount = Rules.discountAmount(targetSubtotal, d.type.name(), d.value);
    BigDecimal remaining = totalDiscount;
    for (int i = 0; i < targetLines.size(); i++) {
      var line = targetLines.get(i);
      BigDecimal lineSub = Rules.lineSubtotal(line.unitPrice, line.quantity);
      BigDecimal lineDisc;
      if (i == targetLines.size() - 1) {
        lineDisc = remaining.min(lineSub).max(BigDecimal.ZERO);
      } else {
        lineDisc =
            totalDiscount
                .multiply(lineSub)
                .divide(targetSubtotal, 2, RoundingMode.HALF_UP)
                .min(lineSub)
                .min(remaining);
      }
      remaining = remaining.subtract(lineDisc);
      result.put(line.id != null ? line.id : (long) i, lineDisc);
    }
    return result;
  }

  private void apply(Discount d, DiscountDtos.Input input) {
    Rules.dates(input.validFrom(), input.validUntil());
    if (input.type() == DiscountType.PERCENTAGE
        && input.value().compareTo(new BigDecimal("100.00")) > 0) {
      throw new IllegalArgumentException("Percentage discount cannot exceed 100.00");
    }
    DiscountScope scope =
        input.scope() != null
            ? input.scope()
            : (input.productId() != null ? DiscountScope.PRODUCT : DiscountScope.SUBSCRIPTION);
    if (scope == DiscountScope.PRODUCT && input.productId() == null) {
      throw new IllegalArgumentException("Product is required for product-scoped discounts");
    }
    Product product = null;
    if (input.productId() != null) {
      product =
          products
              .findById(input.productId())
              .map(v -> org.hibernate.Hibernate.unproxy(v, Product.class))
              .orElseThrow(() -> ApiException.notFound("Product"));
    }
    Subscription subscription = null;
    if (input.subscriptionId() != null) {
      subscription =
          subscriptions
              .findById(input.subscriptionId())
              .map(v -> org.hibernate.Hibernate.unproxy(v, Subscription.class))
              .orElseThrow(() -> ApiException.notFound("Subscription"));
    }
    if (input.usageLimit() != null && input.usageLimit() < d.usedCount) {
      throw new IllegalArgumentException(
          "Usage limit cannot be less than already used count (" + d.usedCount + ")");
    }
    d.name = input.name().trim();
    d.code =
        input.code() == null || input.code().isBlank()
            ? null
            : input.code().trim().toUpperCase(Locale.ROOT);
    d.description = input.description() == null ? null : input.description().trim();
    d.type = input.type();
    d.value = input.value().setScale(2, RoundingMode.UNNECESSARY);
    d.scope = scope;
    d.product = product;
    d.subscription = subscription;
    d.minimumQuantity = input.minimumQuantity();
    d.minimumPurchase =
        input.minimumPurchase() == null
            ? null
            : input.minimumPurchase().setScale(2, RoundingMode.UNNECESSARY);
    d.validFrom = input.validFrom();
    d.validUntil = input.validUntil();
    d.usageLimit = input.usageLimit();
    d.status = input.status() == null ? AccountStatus.ACTIVE : input.status();
  }
}

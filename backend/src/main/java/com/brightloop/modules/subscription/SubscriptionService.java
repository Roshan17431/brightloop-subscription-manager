package com.brightloop.modules.subscription;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.customer.*;
import com.brightloop.modules.plan.*;
import com.brightloop.modules.product.*;
import com.brightloop.modules.user.Role;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SubscriptionService {
  private final SubscriptionRepository subscriptions;
  private final CustomerRepository customers;
  private final ProductRepository products;
  private final PlanRepository plans;
  private final CurrentUser current;
  private final AuditService audit;

  public SubscriptionService(
      SubscriptionRepository subscriptions,
      CustomerRepository customers,
      ProductRepository products,
      PlanRepository plans,
      CurrentUser current,
      AuditService audit) {
    this.subscriptions = subscriptions;
    this.customers = customers;
    this.products = products;
    this.plans = plans;
    this.current = current;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<SubscriptionDtos.View> list(
      String q, SubscriptionState state, int page, int size) {
    var user = current.get();
    Long customerId = null;
    if (user.role == Role.CUSTOMER)
      customerId =
          customers
              .findByUserId(user.id)
              .map(value -> org.hibernate.Hibernate.unproxy(value, Customer.class))
              .orElseThrow(() -> ApiException.notFound("Customer profile"))
              .id;
    return PageResponse.from(
        subscriptions.search(
            PageResponse.search(q), state, customerId, PageResponse.request(page, size)),
        SubscriptionDtos::view);
  }

  private Subscription owned(Subscription s) {
    var u = current.get();
    if (u.role == Role.CUSTOMER
        && (s.customer.getUser() == null || !s.customer.getUser().getId().equals(u.id)))
      throw ApiException.forbidden();
    return s;
  }

  @Transactional(readOnly = true)
  public SubscriptionDtos.View get(Long id) {
    return SubscriptionDtos.view(
        owned(
            subscriptions
                .findById(id)
                .map(value -> org.hibernate.Hibernate.unproxy(value, Subscription.class))
                .orElseThrow(() -> ApiException.notFound("Subscription"))));
  }

  @Transactional
  public SubscriptionDtos.View create(SubscriptionDtos.Input input) {
    var s = new Subscription();
    s.number = "BL-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    apply(s, input);
    subscriptions.saveAndFlush(s);
    audit.record("SUBSCRIPTION_CREATED", "SUBSCRIPTION", s.id, "number=" + s.number);
    return SubscriptionDtos.view(s);
  }

  @Transactional
  public SubscriptionDtos.View update(Long id, SubscriptionDtos.Input input) {
    var s =
        owned(
            subscriptions
                .lockById(id)
                .map(value -> org.hibernate.Hibernate.unproxy(value, Subscription.class))
                .orElseThrow(() -> ApiException.notFound("Subscription")));
    if (s.state != SubscriptionState.DRAFT)
      throw ApiException.conflict("Only draft subscriptions can be edited");
    apply(s, input);
    subscriptions.saveAndFlush(s);
    audit.record("SUBSCRIPTION_UPDATED", "SUBSCRIPTION", s.id, "draft edited");
    return SubscriptionDtos.view(s);
  }

  @Transactional
  public SubscriptionDtos.View transition(Long id, SubscriptionState next) {
    current.requireManager();
    var s =
        subscriptions
            .lockById(id)
            .map(value -> org.hibernate.Hibernate.unproxy(value, Subscription.class))
            .orElseThrow(() -> ApiException.notFound("Subscription"));
    try {
      Rules.transition(s.state.name(), next.name());
    } catch (IllegalArgumentException e) {
      throw ApiException.conflict(e.getMessage());
    }
    var previous = s.state;
    s.state = next;
    if (next == SubscriptionState.ACTIVE && s.lastBilledDate == null) {
      s.nextBillingDate = s.startDate;
    }
    subscriptions.saveAndFlush(s);
    audit.record(
        "SUBSCRIPTION_STATE_CHANGED", "SUBSCRIPTION", s.id, "from=" + previous + ",to=" + next);
    return SubscriptionDtos.view(s);
  }

  private void apply(Subscription s, SubscriptionDtos.Input input) {
    Rules.dates(input.startDate(), input.expiryDate());
    var user = current.get();
    Customer customer;
    if (user.role == Role.CUSTOMER) {
      customer =
          customers
              .findByUserId(user.id)
              .map(value -> org.hibernate.Hibernate.unproxy(value, Customer.class))
              .orElseThrow(() -> ApiException.notFound("Customer profile"));
      if (input.customerId() != null && !input.customerId().equals(customer.id))
        throw ApiException.forbidden();
    } else {
      if (input.customerId() == null) throw new IllegalArgumentException("Customer is required");
      customer =
          customers
              .findById(input.customerId())
              .map(value -> org.hibernate.Hibernate.unproxy(value, Customer.class))
              .orElseThrow(() -> ApiException.notFound("Customer"));
    }
    if (customer.status != AccountStatus.ACTIVE)
      throw new IllegalArgumentException("Customer account must be active");
    var plan =
        plans
            .findById(input.planId())
            .map(value -> org.hibernate.Hibernate.unproxy(value, Plan.class))
            .orElseThrow(() -> ApiException.notFound("Plan"));
    if (plan.status != AccountStatus.ACTIVE)
      throw new IllegalArgumentException("Plan must be active");
    if ((plan.availableFrom != null && input.startDate().isBefore(plan.availableFrom))
        || (plan.availableUntil != null && input.startDate().isAfter(plan.availableUntil)))
      throw new IllegalArgumentException("Subscription start date is outside plan availability");
    long quantity = input.lines().stream().mapToLong(SubscriptionDtos.LineInput::quantity).sum();
    if (quantity < plan.minimumQuantity)
      throw new IllegalArgumentException("Minimum total quantity is " + plan.minimumQuantity);
    var allowed = new HashSet<Long>();
    plan.products.forEach(p -> allowed.add(p.getId()));
    var ids =
        input.lines().stream()
            .map(SubscriptionDtos.LineInput::productId)
            .collect(java.util.stream.Collectors.toSet());
    var productMap = new HashMap<Long, Product>();
    products
        .findAllById(ids)
        .forEach(p -> productMap.put(p.getId(), org.hibernate.Hibernate.unproxy(p, Product.class)));
    s.lines.clear();
    var total = BigDecimal.ZERO;
    for (var in : input.lines()) {
      var p = productMap.get(in.productId());
      if (p == null) throw ApiException.notFound("Product");
      if (!allowed.contains(p.id))
        throw new IllegalArgumentException("Product is not associated with the selected plan");
      if (p.status != AccountStatus.ACTIVE)
        throw new IllegalArgumentException("Subscription products must be active");
      ProductVariant variant = null;
      if (in.variantId() != null)
        variant =
            p.variants.stream()
                .filter(v -> v.getId().equals(in.variantId()))
                .map(value -> org.hibernate.Hibernate.unproxy(value, ProductVariant.class))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Variant does not belong to the selected product"));
      var line = new SubscriptionLine();
      line.subscription = s;
      line.product = p;
      line.productName = p.name;
      line.variant = variant;
      line.variantName = variant == null ? null : variant.name;
      line.quantity = in.quantity();
      line.unitPrice =
          Rules.unitPrice(
              p.salesPrice, variant == null ? BigDecimal.ZERO : variant.priceAdjustment);
      line.taxRate = in.taxRate();
      line.amount = Rules.lineAmount(line.unitPrice, line.quantity, line.taxRate);
      total = total.add(line.amount);
      s.lines.add(line);
    }
    if (total.compareTo(new BigDecimal("99999999999999999.99")) > 0)
      throw new IllegalArgumentException("Subscription total exceeds supported amount");
    s.customer = customer;
    s.plan = plan;
    s.planName = plan.name;
    s.startDate = input.startDate();
    s.expiryDate = input.expiryDate();
    s.nextBillingDate = input.startDate();
    s.paymentTerms = input.paymentTerms().trim();
    s.total = total;
  }
}

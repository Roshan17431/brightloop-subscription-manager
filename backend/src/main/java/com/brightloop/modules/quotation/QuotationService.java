package com.brightloop.modules.quotation;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.customer.Customer;
import com.brightloop.modules.customer.CustomerRepository;
import com.brightloop.modules.plan.Plan;
import com.brightloop.modules.plan.PlanRepository;
import com.brightloop.modules.product.Product;
import com.brightloop.modules.product.ProductRepository;
import com.brightloop.modules.product.ProductVariant;
import com.brightloop.modules.subscription.Subscription;
import com.brightloop.modules.subscription.SubscriptionDtos;
import com.brightloop.modules.subscription.SubscriptionLine;
import com.brightloop.modules.subscription.SubscriptionRepository;
import com.brightloop.modules.subscription.SubscriptionState;
import com.brightloop.modules.user.Role;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuotationService {
  private final QuotationTemplateRepository templates;
  private final QuotationRepository quotations;
  private final SubscriptionRepository subscriptions;
  private final CustomerRepository customers;
  private final PlanRepository plans;
  private final ProductRepository products;
  private final CurrentUser current;
  private final AuditService audit;

  public QuotationService(
      QuotationTemplateRepository templates,
      QuotationRepository quotations,
      SubscriptionRepository subscriptions,
      CustomerRepository customers,
      PlanRepository plans,
      ProductRepository products,
      CurrentUser current,
      AuditService audit) {
    this.templates = templates;
    this.quotations = quotations;
    this.subscriptions = subscriptions;
    this.customers = customers;
    this.plans = plans;
    this.products = products;
    this.current = current;
    this.audit = audit;
  }

  @Transactional(readOnly = true)
  public PageResponse<QuotationDtos.TemplateView> listTemplates(
      String q, AccountStatus status, Long planId, int page, int size) {
    current.requireManager();
    return PageResponse.from(
        templates.search(
            PageResponse.search(q), status, planId, PageResponse.request(page, size)),
        QuotationDtos::viewTemplate);
  }

  @Transactional(readOnly = true)
  public QuotationDtos.TemplateView getTemplate(Long id) {
    current.requireManager();
    return QuotationDtos.viewTemplate(
        templates
            .findById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, QuotationTemplate.class))
            .orElseThrow(() -> ApiException.notFound("Quotation template")));
  }

  @Transactional
  public QuotationDtos.TemplateView createTemplate(QuotationDtos.TemplateInput input) {
    current.requireManager();
    String name = input.name().trim();
    if (templates.findByNameIgnoreCase(name).isPresent()) {
      throw ApiException.conflict("Quotation template name already exists: " + name);
    }
    var t = new QuotationTemplate();
    applyTemplate(t, input);
    templates.saveAndFlush(t);
    audit.record(
        "QUOTATION_TEMPLATE_CREATED", "QUOTATION_TEMPLATE", t.id, "name=" + t.name);
    return QuotationDtos.viewTemplate(t);
  }

  @Transactional
  public QuotationDtos.TemplateView updateTemplate(Long id, QuotationDtos.TemplateInput input) {
    current.requireManager();
    var t =
        templates
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, QuotationTemplate.class))
            .orElseThrow(() -> ApiException.notFound("Quotation template"));
    String name = input.name().trim();
    var existing = templates.findByNameIgnoreCase(name);
    if (existing.isPresent() && !existing.get().id.equals(t.id)) {
      throw ApiException.conflict("Quotation template name already exists: " + name);
    }
    applyTemplate(t, input);
    templates.saveAndFlush(t);
    audit.record(
        "QUOTATION_TEMPLATE_UPDATED", "QUOTATION_TEMPLATE", t.id, "name=" + t.name);
    return QuotationDtos.viewTemplate(t);
  }

  private void applyTemplate(QuotationTemplate t, QuotationDtos.TemplateInput input) {
    var plan =
        plans
            .findById(input.planId())
            .map(v -> org.hibernate.Hibernate.unproxy(v, Plan.class))
            .orElseThrow(() -> ApiException.notFound("Plan"));
    if (plan.status != AccountStatus.ACTIVE) {
      throw new IllegalArgumentException("Plan must be active");
    }
    long totalQuantity =
        input.lines().stream().mapToLong(QuotationDtos.TemplateLineInput::quantity).sum();
    if (totalQuantity < plan.minimumQuantity) {
      throw new IllegalArgumentException("Minimum total quantity is " + plan.minimumQuantity);
    }
    var allowedProductIds = new HashSet<Long>();
    plan.products.forEach(p -> allowedProductIds.add(p.getId()));

    var productIds =
        input.lines().stream()
            .map(QuotationDtos.TemplateLineInput::productId)
            .collect(java.util.stream.Collectors.toSet());
    var productMap = new HashMap<Long, Product>();
    products
        .findAllById(productIds)
        .forEach(p -> productMap.put(p.getId(), org.hibernate.Hibernate.unproxy(p, Product.class)));

    t.name = input.name().trim();
    t.description =
        input.description() == null || input.description().isBlank()
            ? null
            : input.description().trim();
    t.plan = plan;
    t.planName = plan.name;
    t.validityDays = input.validityDays();
    t.paymentTerms = input.paymentTerms().trim();
    t.status = input.status();
    t.lines.clear();

    for (var in : input.lines()) {
      var p = productMap.get(in.productId());
      if (p == null) throw ApiException.notFound("Product");
      if (!allowedProductIds.contains(p.id)) {
        throw new IllegalArgumentException("Product is not associated with the selected plan");
      }
      if (p.status != AccountStatus.ACTIVE) {
        throw new IllegalArgumentException("Template products must be active");
      }
      ProductVariant variant = null;
      if (in.variantId() != null) {
        variant =
            p.variants.stream()
                .filter(v -> v.getId().equals(in.variantId()))
                .map(v -> org.hibernate.Hibernate.unproxy(v, ProductVariant.class))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Variant does not belong to the selected product"));
      }
      BigDecimal unitPrice =
          in.unitPrice() != null
              ? in.unitPrice().setScale(2, RoundingMode.HALF_UP)
              : Rules.unitPrice(
                  p.salesPrice, variant == null ? BigDecimal.ZERO : variant.priceAdjustment);
      if (unitPrice.signum() < 0) {
        throw new IllegalArgumentException("Unit price cannot be negative");
      }
      var line = new QuotationTemplateLine();
      line.template = t;
      line.product = p;
      line.productName = p.name;
      line.variant = variant;
      line.variantName = variant == null ? null : variant.name;
      line.quantity = in.quantity();
      line.unitPrice = unitPrice;
      line.taxRate = in.taxRate().setScale(2, RoundingMode.HALF_UP);
      line.amount = Rules.lineAmount(line.unitPrice, line.quantity, line.taxRate);
      t.lines.add(line);
    }
  }

  private Quotation ownedQuotation(Quotation q) {
    var u = current.get();
    if (u.role == Role.CUSTOMER
        && (q.customer.getUser() == null || !q.customer.getUser().getId().equals(u.id))) {
      throw ApiException.forbidden();
    }
    return q;
  }

  @Transactional(readOnly = true)
  public PageResponse<QuotationDtos.QuotationView> listQuotations(
      String q, QuotationState state, Long customerId, Long templateId, int page, int size) {
    var user = current.get();
    Long effectiveCustomerId = customerId;
    if (user.role == Role.CUSTOMER) {
      Long ownCustomerId =
          customers
              .findByUserId(user.id)
              .map(v -> org.hibernate.Hibernate.unproxy(v, Customer.class))
              .orElseThrow(() -> ApiException.notFound("Customer profile"))
              .id;
      if (customerId != null && !customerId.equals(ownCustomerId)) {
        throw ApiException.forbidden();
      }
      effectiveCustomerId = ownCustomerId;
    }
    return PageResponse.from(
        quotations.search(
            PageResponse.search(q),
            state,
            effectiveCustomerId,
            templateId,
            PageResponse.request(page, size)),
        QuotationDtos::viewQuotation);
  }

  @Transactional(readOnly = true)
  public QuotationDtos.QuotationView getQuotation(Long id) {
    return QuotationDtos.viewQuotation(
        ownedQuotation(
            quotations
                .findById(id)
                .map(v -> org.hibernate.Hibernate.unproxy(v, Quotation.class))
                .orElseThrow(() -> ApiException.notFound("Quotation"))));
  }

  @Transactional
  public QuotationDtos.QuotationView generateFromTemplate(
      Long templateId, QuotationDtos.GenerateInput input) {
    current.requireManager();
    var user = current.get();

    Long effectiveTemplateId = templateId != null ? templateId : input.templateId();
    if (effectiveTemplateId == null) {
      throw new IllegalArgumentException("Quotation template ID is required");
    }

    var template =
        templates
            .findById(effectiveTemplateId)
            .map(v -> org.hibernate.Hibernate.unproxy(v, QuotationTemplate.class))
            .orElseThrow(() -> ApiException.notFound("Quotation template"));
    if (template.status != AccountStatus.ACTIVE) {
      throw ApiException.conflict("Quotation template must be active");
    }

    var customer =
        customers
            .findById(input.customerId())
            .map(v -> org.hibernate.Hibernate.unproxy(v, Customer.class))
            .orElseThrow(() -> ApiException.notFound("Customer"));
    if (customer.status != AccountStatus.ACTIVE) {
      throw new IllegalArgumentException("Customer account must be active");
    }

    var plan = org.hibernate.Hibernate.unproxy(template.plan, Plan.class);
    if (plan.status != AccountStatus.ACTIVE) {
      throw new IllegalArgumentException("Plan must be active");
    }

    LocalDate issueDate = input.issueDate() != null ? input.issueDate() : LocalDate.now();
    LocalDate validUntil =
        input.validUntil() != null
            ? input.validUntil()
            : issueDate.plusDays(template.validityDays);
    Rules.dates(issueDate, validUntil);
    Rules.dates(input.startDate(), input.expiryDate());

    var q = new Quotation();
    q.quotationNumber =
        "QTN-"
            + issueDate.toString().replace("-", "")
            + "-"
            + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    q.template = template;
    q.templateName = template.name;
    q.customer = customer;
    q.customerName = customer.name;
    q.plan = plan;
    q.planName = plan.name;
    q.issueDate = issueDate;
    q.validUntil = validUntil;
    q.startDate = input.startDate();
    q.expiryDate = input.expiryDate();
    q.paymentTerms =
        (input.paymentTerms() != null && !input.paymentTerms().isBlank())
            ? input.paymentTerms().trim()
            : template.paymentTerms;
    q.state = QuotationState.SENT;
    q.notes = input.notes() == null || input.notes().isBlank() ? null : input.notes().trim();
    q.createdBy = user.email;

    BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
    BigDecimal taxAmount = BigDecimal.ZERO.setScale(2);
    BigDecimal total = BigDecimal.ZERO.setScale(2);

    if (input.lines() != null && !input.lines().isEmpty()) {
      var allowedProductIds = new HashSet<Long>();
      plan.products.forEach(p -> allowedProductIds.add(p.getId()));
      var productIds =
          input.lines().stream()
              .map(QuotationDtos.TemplateLineInput::productId)
              .collect(java.util.stream.Collectors.toSet());
      var productMap = new HashMap<Long, Product>();
      products
          .findAllById(productIds)
          .forEach(
              p -> productMap.put(p.getId(), org.hibernate.Hibernate.unproxy(p, Product.class)));

      long totalQty =
          input.lines().stream().mapToLong(QuotationDtos.TemplateLineInput::quantity).sum();
      if (totalQty < plan.minimumQuantity) {
        throw new IllegalArgumentException("Minimum total quantity is " + plan.minimumQuantity);
      }

      for (var in : input.lines()) {
        var p = productMap.get(in.productId());
        if (p == null) throw ApiException.notFound("Product");
        if (!allowedProductIds.contains(p.id)) {
          throw new IllegalArgumentException("Product is not associated with the selected plan");
        }
        ProductVariant variant = null;
        if (in.variantId() != null) {
          variant =
              p.variants.stream()
                  .filter(v -> v.getId().equals(in.variantId()))
                  .map(v -> org.hibernate.Hibernate.unproxy(v, ProductVariant.class))
                  .findFirst()
                  .orElseThrow(
                      () ->
                          new IllegalArgumentException(
                              "Variant does not belong to the selected product"));
        }
        BigDecimal unitPrice =
            in.unitPrice() != null
                ? in.unitPrice().setScale(2, RoundingMode.HALF_UP)
                : Rules.unitPrice(
                    p.salesPrice, variant == null ? BigDecimal.ZERO : variant.priceAdjustment);
        var ql = new QuotationLine();
        ql.quotation = q;
        ql.product = p;
        ql.productName = p.name;
        ql.variant = variant;
        ql.variantName = variant == null ? null : variant.name;
        ql.quantity = in.quantity();
        ql.unitPrice = unitPrice;
        ql.subtotal = Rules.lineSubtotal(ql.unitPrice, ql.quantity);
        ql.taxRate = in.taxRate().setScale(2, RoundingMode.HALF_UP);
        ql.taxAmount = Rules.taxAmount(ql.subtotal, ql.taxRate);
        ql.amount = ql.subtotal.add(ql.taxAmount);

        subtotal = subtotal.add(ql.subtotal);
        taxAmount = taxAmount.add(ql.taxAmount);
        total = total.add(ql.amount);
        q.lines.add(ql);
      }
    } else {
      for (var tl : template.lines) {
        var prod = org.hibernate.Hibernate.unproxy(tl.product, Product.class);
        var variant =
            tl.variant == null
                ? null
                : org.hibernate.Hibernate.unproxy(tl.variant, ProductVariant.class);
        var ql = new QuotationLine();
        ql.quotation = q;
        ql.product = prod;
        ql.productName = tl.productName;
        ql.variant = variant;
        ql.variantName = tl.variantName;
        ql.quantity = tl.quantity;
        ql.unitPrice = tl.unitPrice;
        ql.subtotal = Rules.lineSubtotal(ql.unitPrice, ql.quantity);
        ql.taxRate = tl.taxRate;
        ql.taxAmount = Rules.taxAmount(ql.subtotal, ql.taxRate);
        ql.amount = ql.subtotal.add(ql.taxAmount);

        subtotal = subtotal.add(ql.subtotal);
        taxAmount = taxAmount.add(ql.taxAmount);
        total = total.add(ql.amount);
        q.lines.add(ql);
      }
    }

    q.subtotal = subtotal;
    q.taxAmount = taxAmount;
    q.total = total;

    quotations.saveAndFlush(q);
    audit.record(
        "QUOTATION_GENERATED",
        "QUOTATION",
        q.id,
        "number="
            + q.quotationNumber
            + ",template="
            + template.name
            + ",customer="
            + customer.name
            + ",validUntil="
            + q.validUntil);
    return QuotationDtos.viewQuotation(q);
  }

  @Transactional
  public QuotationDtos.QuotationView confirm(Long id, QuotationDtos.ConfirmInput input) {
    current.requireManager();
    var q =
        quotations
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Quotation.class))
            .orElseThrow(() -> ApiException.notFound("Quotation"));
    if (q.state == QuotationState.CONVERTED) {
      throw ApiException.conflict("Quotation has already been converted to a subscription");
    }
    if (q.state == QuotationState.CANCELLED) {
      throw ApiException.conflict("Cannot confirm a cancelled quotation");
    }
    LocalDate evalDate =
        (input != null && input.asOfDate() != null) ? input.asOfDate() : LocalDate.now();
    if (q.state == QuotationState.EXPIRED || q.validUntil.isBefore(evalDate)) {
      q.state = QuotationState.EXPIRED;
      quotations.saveAndFlush(q);
      throw ApiException.conflict(
          "Quotation " + q.quotationNumber + " expired on " + q.validUntil);
    }
    q.state = QuotationState.CONFIRMED;
    q.confirmedAt = Instant.now();
    quotations.saveAndFlush(q);
    audit.record("QUOTATION_CONFIRMED", "QUOTATION", q.id, "number=" + q.quotationNumber);
    return QuotationDtos.viewQuotation(q, evalDate);
  }

  @Transactional
  public QuotationDtos.QuotationView cancel(Long id) {
    current.requireManager();
    var q =
        quotations
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Quotation.class))
            .orElseThrow(() -> ApiException.notFound("Quotation"));
    if (q.state == QuotationState.CONVERTED) {
      throw ApiException.conflict("Cannot cancel a quotation that has already been converted");
    }
    q.state = QuotationState.CANCELLED;
    q.cancelledAt = Instant.now();
    quotations.saveAndFlush(q);
    audit.record("QUOTATION_CANCELLED", "QUOTATION", q.id, "number=" + q.quotationNumber);
    return QuotationDtos.viewQuotation(q);
  }

  @Transactional
  public SubscriptionDtos.View convertToSubscription(Long id, QuotationDtos.ConvertInput input) {
    current.requireManager();
    var q =
        quotations
            .lockById(id)
            .map(v -> org.hibernate.Hibernate.unproxy(v, Quotation.class))
            .orElseThrow(() -> ApiException.notFound("Quotation"));
    if (q.state == QuotationState.CONVERTED || q.convertedSubscription != null) {
      throw ApiException.conflict(
          "Quotation "
              + q.quotationNumber
              + " has already been converted to subscription "
              + q.convertedSubscriptionNumber);
    }
    if (q.state == QuotationState.CANCELLED) {
      throw ApiException.conflict("Cannot convert a cancelled quotation");
    }

    LocalDate evalDate =
        (input != null && input.asOfDate() != null) ? input.asOfDate() : LocalDate.now();
    if (q.state == QuotationState.EXPIRED || q.validUntil.isBefore(evalDate)) {
      q.state = QuotationState.EXPIRED;
      quotations.saveAndFlush(q);
      throw ApiException.conflict(
          "Cannot convert expired quotation "
              + q.quotationNumber
              + " (valid until "
              + q.validUntil
              + ")");
    }

    var customer = org.hibernate.Hibernate.unproxy(q.customer, Customer.class);
    var plan = org.hibernate.Hibernate.unproxy(q.plan, Plan.class);

    LocalDate startDate =
        (input != null && input.startDate() != null) ? input.startDate() : q.startDate;
    LocalDate expiryDate =
        (input != null && input.expiryDate() != null) ? input.expiryDate() : q.expiryDate;
    Rules.dates(startDate, expiryDate);

    boolean activateImmediately =
        input == null
            || input.activateImmediately() == null
            || input.activateImmediately();

    var s = new Subscription();
    s.number = "BL-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
    s.customer = customer;
    s.plan = plan;
    s.planName = q.planName;
    s.startDate = startDate;
    s.expiryDate = expiryDate;
    s.nextBillingDate = startDate;
    s.paymentTerms = q.paymentTerms;
    s.state = activateImmediately ? SubscriptionState.ACTIVE : SubscriptionState.CONFIRMED;
    s.quotationTemplateId = q.template == null ? null : q.template.getId();
    s.quotationId = q.id;
    s.quotationValidUntil = q.validUntil;
    s.convertedFromQuotationAt = Instant.now();

    BigDecimal total = BigDecimal.ZERO.setScale(2);
    for (var ql : q.lines) {
      var prod = org.hibernate.Hibernate.unproxy(ql.product, Product.class);
      var variant =
          ql.variant == null
              ? null
              : org.hibernate.Hibernate.unproxy(ql.variant, ProductVariant.class);
      var sl = new SubscriptionLine();
      sl.subscription = s;
      sl.product = prod;
      sl.productName = ql.productName;
      sl.variant = variant;
      sl.variantName = ql.variantName;
      sl.quantity = ql.quantity;
      sl.unitPrice = ql.unitPrice;
      sl.taxRate = ql.taxRate;
      sl.amount = ql.amount;
      total = total.add(sl.amount);
      s.lines.add(sl);
    }
    s.total = total;

    subscriptions.saveAndFlush(s);

    Instant now = Instant.now();
    if (q.confirmedAt == null) q.confirmedAt = now;
    q.convertedAt = now;
    q.state = QuotationState.CONVERTED;
    q.convertedSubscription = s;
    q.convertedSubscriptionNumber = s.number;
    quotations.saveAndFlush(q);

    audit.record(
        "QUOTATION_CONVERTED",
        "QUOTATION",
        q.id,
        "quotation=" + q.quotationNumber + ",subscription=" + s.number);
    audit.record(
        "SUBSCRIPTION_CREATED_FROM_QUOTATION",
        "SUBSCRIPTION",
        s.id,
        "subscription=" + s.number + ",quotation=" + q.quotationNumber);

    return SubscriptionDtos.view(s);
  }
}

package com.brightloop.modules.plan;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.product.ProductRepository;
import com.brightloop.response.PageResponse;
import com.brightloop.security.CurrentUser;
import com.brightloop.validation.Rules;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanService {
  private final PlanRepository plans;
  private final ProductRepository products;
  private final CurrentUser current;
  private final AuditService audit;

  public PlanService(
      PlanRepository plans, ProductRepository products, CurrentUser current, AuditService audit) {
    this.plans = plans;
    this.products = products;
    this.current = current;
    this.audit = audit;
  }

  public Plan find(Long id) {
    return plans
        .findById(id)
        .map(value -> org.hibernate.Hibernate.unproxy(value, Plan.class))
        .orElseThrow(() -> ApiException.notFound("Plan"));
  }

  @Transactional(readOnly = true)
  public PageResponse<PlanDtos.View> list(String q, AccountStatus status, int page, int size) {
    return PageResponse.from(
        plans.search(PageResponse.search(q), status, PageResponse.request(page, size)),
        PlanDtos::view);
  }

  @Transactional(readOnly = true)
  public PlanDtos.View get(Long id) {
    return PlanDtos.view(find(id));
  }

  @Transactional
  public PlanDtos.View create(PlanDtos.Input input) {
    current.requireAdmin();
    var p = new Plan();
    apply(p, input);
    plans.saveAndFlush(p);
    audit.record("PLAN_CREATED", "PLAN", p.id, "billingPeriod=" + p.billingPeriod);
    return PlanDtos.view(p);
  }

  @Transactional
  public PlanDtos.View update(Long id, PlanDtos.Input input) {
    current.requireAdmin();
    var p = find(id);
    apply(p, input);
    plans.saveAndFlush(p);
    audit.record("PLAN_UPDATED", "PLAN", p.id, "plan definition updated");
    return PlanDtos.view(p);
  }

  @Transactional
  public void delete(Long id) {
    current.requireAdmin();
    plans.delete(find(id));
    plans.flush();
    audit.record("PLAN_DELETED", "PLAN", id, "plan removal");
  }

  private void apply(Plan p, PlanDtos.Input i) {
    Rules.dates(i.availableFrom(), i.availableUntil());
    if (new HashSet<>(i.productIds()).size() != i.productIds().size())
      throw new IllegalArgumentException("Duplicate plan product");
    var associated = products.findAllById(i.productIds());
    if (associated.size() != i.productIds().size())
      throw new IllegalArgumentException("One or more plan products do not exist");
    p.name = i.name().trim();
    p.description = i.description();
    p.billingPeriod = i.billingPeriod();
    p.price = i.price();
    p.minimumQuantity = i.minimumQuantity();
    p.availableFrom = i.availableFrom();
    p.availableUntil = i.availableUntil();
    p.automaticClosing = i.automaticClosing();
    p.earlyClosingAllowed = i.earlyClosingAllowed();
    p.pausingAllowed = i.pausingAllowed();
    p.renewalAllowed = i.renewalAllowed();
    p.status = i.status() == null ? AccountStatus.ACTIVE : i.status();
    p.products.clear();
    p.products.addAll(associated);
  }
}

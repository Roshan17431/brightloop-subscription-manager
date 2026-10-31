package com.brightloop.modules.customer;

import com.brightloop.config.AccountStatus;
import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.user.*;
import com.brightloop.response.PageResponse;
import com.brightloop.security.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {
  private final CustomerRepository customers;
  private final UserRepository users;
  private final CurrentUser current;
  private final AuditService audit;
  private final SessionRevoker sessions;

  public CustomerService(
      CustomerRepository customers,
      UserRepository users,
      CurrentUser current,
      AuditService audit,
      SessionRevoker sessions) {
    this.customers = customers;
    this.users = users;
    this.current = current;
    this.audit = audit;
    this.sessions = sessions;
  }

  @Transactional(readOnly = true)
  public PageResponse<CustomerDtos.View> list(String q, AccountStatus status, int page, int size) {
    current.requireManager();
    return PageResponse.from(
        customers.search(PageResponse.search(q), status, PageResponse.request(page, size)),
        CustomerDtos::view);
  }

  public Customer owned(Long id) {
    var c =
        customers
            .findById(id)
            .map(value -> org.hibernate.Hibernate.unproxy(value, Customer.class))
            .orElseThrow(() -> ApiException.notFound("Customer"));
    var u = current.get();
    if (u.role == Role.CUSTOMER && (c.user == null || !c.user.getId().equals(u.id)))
      throw ApiException.forbidden();
    return c;
  }

  @Transactional(readOnly = true)
  public CustomerDtos.View get(Long id) {
    return CustomerDtos.view(owned(id));
  }

  @Transactional(readOnly = true)
  public CustomerDtos.View me() {
    return CustomerDtos.view(
        customers
            .findByUserId(current.get().id)
            .orElseThrow(() -> ApiException.notFound("Customer profile")));
  }

  @Transactional
  public CustomerDtos.View updateMe(CustomerDtos.Input input) {
    var c =
        customers
            .findByUserId(current.get().id)
            .orElseThrow(() -> ApiException.notFound("Customer profile"));
    return update(c.id, input);
  }

  @Transactional
  public CustomerDtos.View create(CustomerDtos.Input input) {
    current.requireManager();
    var c = new Customer();
    apply(c, input);
    customers.saveAndFlush(c);
    audit.record("CUSTOMER_CREATED", "CUSTOMER", c.id, "manual customer");
    return CustomerDtos.view(c);
  }

  @Transactional
  public CustomerDtos.View update(Long id, CustomerDtos.Input input) {
    var c = owned(id);
    if (current.get().role == Role.CUSTOMER && c.status != input.status())
      throw ApiException.forbidden();
    apply(c, input);
    customers.saveAndFlush(c);
    audit.record("CUSTOMER_UPDATED", "CUSTOMER", c.id, "profile fields updated");
    return CustomerDtos.view(c);
  }

  private void apply(Customer c, CustomerDtos.Input input) {
    var email = UserService.email(input.email());
    customers
        .findByEmail(email)
        .filter(other -> !other.id.equals(c.id))
        .ifPresent(
            other -> {
              throw ApiException.conflict("Email is already in use");
            });
    users
        .findByEmail(email)
        .filter(other -> c.user == null || !other.getId().equals(c.user.getId()))
        .ifPresent(
            other -> {
              throw ApiException.conflict("Email is already in use");
            });
    if (c.user != null) {
      if (!c.user.getEmail().equals(email)) sessions.revoke(c.user.getEmail());
      c.user.setEmail(email);
      c.user.setName(input.name().trim());
    }
    c.name = input.name().trim();
    c.email = email;
    c.phone = input.phone();
    c.companyName = input.companyName();
    c.billingAddress = input.billingAddress();
    c.status = input.status();
  }
}

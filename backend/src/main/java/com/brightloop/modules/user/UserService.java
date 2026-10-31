package com.brightloop.modules.user;

import com.brightloop.exception.ApiException;
import com.brightloop.modules.audit.AuditService;
import com.brightloop.modules.auth.AuthDtos.Register;
import com.brightloop.modules.customer.*;
import com.brightloop.response.*;
import com.brightloop.security.*;
import com.brightloop.validation.Rules;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository users;
  private final CustomerRepository customers;
  private final PasswordEncoder encoder;
  private final AuditService audit;
  private final CurrentUser current;
  private final SessionRevoker sessions;

  public UserService(
      UserRepository users,
      CustomerRepository customers,
      PasswordEncoder encoder,
      AuditService audit,
      CurrentUser current,
      SessionRevoker sessions) {
    this.users = users;
    this.customers = customers;
    this.encoder = encoder;
    this.audit = audit;
    this.current = current;
    this.sessions = sessions;
  }

  public static String email(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  @Transactional
  public UserDto register(Register input) {
    return dto(create(input, Role.CUSTOMER));
  }

  @Transactional
  public UserAccount create(Register input, Role role) {
    Rules.password(input.password());
    var email = email(input.email());
    if (users.existsByEmail(email) || customers.findByEmail(email).isPresent())
      throw ApiException.conflict("Email is already registered");
    var user = new UserAccount();
    user.name = input.name().trim();
    user.email = email;
    user.passwordHash = encoder.encode(input.password());
    user.role = role;
    users.saveAndFlush(user);
    if (role == Role.CUSTOMER) {
      var c = new Customer();
      c.user = user;
      c.name = user.name;
      c.email = user.email;
      customers.saveAndFlush(c);
      audit.record("CUSTOMER_CREATED", "CUSTOMER", c.id, "registration");
    }
    audit.record(
        role == Role.STAFF ? "STAFF_CREATED" : "USER_CREATED", "USER", user.id, "role=" + role);
    return user;
  }

  @Transactional(readOnly = true)
  public UserDto me() {
    return dto(current.get());
  }

  public UserDto dto(UserAccount user) {
    return new UserDto(
        user.id,
        user.name,
        user.email,
        user.role,
        customers.findByUserId(user.id).map(c -> c.id).orElse(null));
  }

  @Transactional
  public UserDto staff(Register input) {
    current.requireAdmin();
    return dto(create(input, Role.STAFF));
  }

  @Transactional(readOnly = true)
  public PageResponse<UserDto> list(String q, int page, int size) {
    current.requireAdmin();
    return PageResponse.from(
        users.search(PageResponse.search(q), PageResponse.request(page, size)),
        u -> new UserDto(u.id, u.name, u.email, u.role, null));
  }

  @Transactional
  public UserDto role(Long id, Role role) {
    users.lockAdministrators();
    current.requireAdmin();
    var actor = current.get();
    var u =
        users
            .lockById(id)
            .map(value -> org.hibernate.Hibernate.unproxy(value, UserAccount.class))
            .orElseThrow(() -> ApiException.notFound("User"));
    if (actor.id.equals(id) && role != Role.ADMIN)
      throw ApiException.conflict("You cannot remove your own administrator role");
    if (u.role == Role.ADMIN && role != Role.ADMIN && users.countByRole(Role.ADMIN) <= 1)
      throw ApiException.conflict("At least one administrator is required");
    var previous = u.role;
    u.role = role;
    if (role == Role.CUSTOMER && customers.findByUserId(id).isEmpty()) {
      var c = new Customer();
      c.user = u;
      c.name = u.name;
      c.email = u.email;
      customers.saveAndFlush(c);
      audit.record("CUSTOMER_CREATED", "CUSTOMER", c.id, "role conversion");
    }
    audit.record("ROLE_CHANGED", "USER", u.id, "from=" + previous + ",to=" + role);
    sessions.revoke(u.email);
    return dto(u);
  }
}

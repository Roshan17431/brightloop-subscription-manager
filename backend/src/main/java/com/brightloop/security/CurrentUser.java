package com.brightloop.security;

import com.brightloop.exception.ApiException;
import com.brightloop.modules.user.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
  private final UserRepository users;

  public CurrentUser(UserRepository users) {
    this.users = users;
  }

  public UserAccount get() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null) throw ApiException.forbidden();
    return users
        .findByEmail(auth.getName())
        .map(u -> org.hibernate.Hibernate.unproxy(u, UserAccount.class))
        .orElseThrow(ApiException::forbidden);
  }

  public void requireManager() {
    if (get().role == Role.CUSTOMER) throw ApiException.forbidden();
  }

  public void requireAdmin() {
    if (get().role != Role.ADMIN) throw ApiException.forbidden();
  }
}

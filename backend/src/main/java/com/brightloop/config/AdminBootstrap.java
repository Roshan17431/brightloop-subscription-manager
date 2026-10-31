package com.brightloop.config;

import com.brightloop.modules.auth.AuthDtos.Register;
import com.brightloop.modules.user.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {
  private final UserService service;
  private final UserRepository users;
  private final String email, password, name;

  public AdminBootstrap(
      UserService service,
      UserRepository users,
      @Value("${app.admin-email}") String email,
      @Value("${app.admin-password}") String password,
      @Value("${app.admin-name}") String name) {
    this.service = service;
    this.users = users;
    this.email = email;
    this.password = password;
    this.name = name;
  }

  public void run(ApplicationArguments args) {
    if (email.isBlank() && password.isBlank()) return;
    if (email.isBlank() || password.isBlank())
      throw new IllegalStateException(
          "Both ADMIN_EMAIL and ADMIN_PASSWORD are required for bootstrap");
    if (!users.existsByEmail(UserService.email(email)))
      service.create(new Register(name, email, password), Role.ADMIN);
  }
}

package com.brightloop.security;

import org.springframework.session.*;
import org.springframework.stereotype.Component;

@Component
public class SessionRevoker {
  private final FindByIndexNameSessionRepository<? extends Session> sessions;

  public SessionRevoker(FindByIndexNameSessionRepository<? extends Session> sessions) {
    this.sessions = sessions;
  }

  public void revoke(String email) {
    sessions.findByPrincipalName(email).keySet().forEach(sessions::deleteById);
  }
}

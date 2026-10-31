package com.brightloop.modules.audit;

import java.time.Instant;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AuditService {
  private final AuditRepository repository;

  public AuditService(AuditRepository repository) {
    this.repository = repository;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public void record(String action, String type, Long id, String metadata) {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    var log = new AuditLog();
    log.actor = auth == null ? "SYSTEM" : auth.getName();
    log.action = action;
    log.entityType = type;
    log.entityId = id;
    log.timestamp = Instant.now();
    log.metadata = metadata;
    repository.save(log);
  }
}

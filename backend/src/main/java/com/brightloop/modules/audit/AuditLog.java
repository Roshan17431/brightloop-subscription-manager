package com.brightloop.modules.audit;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, length = 254)
  public String actor;

  @Column(nullable = false, length = 80)
  public String action;

  @Column(nullable = false, length = 40)
  public String entityType;

  @Column(nullable = false)
  public Long entityId;

  @Column(nullable = false)
  public Instant timestamp;

  @Column(nullable = false, length = 2000)
  public String metadata;
}

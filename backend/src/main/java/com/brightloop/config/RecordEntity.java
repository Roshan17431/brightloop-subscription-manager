package com.brightloop.config;

import jakarta.persistence.*;
import java.time.Instant;

@MappedSuperclass
public abstract class RecordEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Long id;

  @Column(nullable = false, updatable = false)
  public Instant createdAt;

  @Column(nullable = false)
  public Instant updatedAt;

  public Long getId() {
    return id;
  }

  @PrePersist
  protected void create() {
    createdAt = updatedAt = Instant.now();
  }

  @PreUpdate
  protected void update() {
    updatedAt = Instant.now();
  }
}

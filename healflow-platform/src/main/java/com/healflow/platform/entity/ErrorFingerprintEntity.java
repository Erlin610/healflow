package com.healflow.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "error_fingerprints")
public class ErrorFingerprintEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 64)
  private String fingerprint;

  @Column(nullable = false)
  private long occurrenceCount;

  @Column(nullable = false)
  private Instant lastSeenTime;

  @Version private long version;

  protected ErrorFingerprintEntity() {}

  public ErrorFingerprintEntity(String fingerprint, Instant firstSeenTime) {
    this.fingerprint = requireNonBlank(fingerprint, "fingerprint");
    this.occurrenceCount = 1L;
    this.lastSeenTime = requireNonNull(firstSeenTime, "firstSeenTime");
  }

  public String getFingerprint() {
    return fingerprint;
  }

  public long getOccurrenceCount() {
    return occurrenceCount;
  }

  public Instant getLastSeenTime() {
    return lastSeenTime;
  }

  public void markSeen(Instant seenAt) {
    this.occurrenceCount += 1L;
    this.lastSeenTime = requireNonNull(seenAt, "seenAt");
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  private static Instant requireNonNull(Instant value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    return value;
  }
}

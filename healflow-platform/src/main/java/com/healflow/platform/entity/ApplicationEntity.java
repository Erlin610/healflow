package com.healflow.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "applications")
public class ApplicationEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 128)
  private String appName;

  @Column(length = 512)
  private String gitUrl;

  @Column(length = 128)
  private String gitBranch;

  @Column(length = 2048)
  private String gitToken;

  @Column(length = 2048)
  private String aiApiKey;

  @Column(nullable = false)
  private boolean autoAnalyze;

  @Column(nullable = false)
  private boolean autoFixProposal;

  @Column(nullable = false)
  private boolean autoCommit;

  @Column(length = 512)
  private String webhookUrl;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected ApplicationEntity() {}

  public ApplicationEntity(String appName) {
    this.appName = requireNonBlank(appName, "appName");
  }

  @PrePersist
  void prePersist() {
    Instant now = Instant.now();
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void preUpdate() {
    updatedAt = Instant.now();
  }

  public String getAppName() {
    return appName;
  }

  public String getGitUrl() {
    return gitUrl;
  }

  public void setGitUrl(String gitUrl) {
    this.gitUrl = gitUrl;
  }

  public String getGitBranch() {
    return gitBranch;
  }

  public void setGitBranch(String gitBranch) {
    this.gitBranch = gitBranch;
  }

  public String getGitToken() {
    return gitToken;
  }

  public void setGitToken(String gitToken) {
    this.gitToken = gitToken;
  }

  public String getAiApiKey() {
    return aiApiKey;
  }

  public void setAiApiKey(String aiApiKey) {
    this.aiApiKey = aiApiKey;
  }

  public boolean isAutoAnalyze() {
    return autoAnalyze;
  }

  public void setAutoAnalyze(boolean autoAnalyze) {
    this.autoAnalyze = autoAnalyze;
  }

  public boolean isAutoFixProposal() {
    return autoFixProposal;
  }

  public void setAutoFixProposal(boolean autoFixProposal) {
    this.autoFixProposal = autoFixProposal;
  }

  public boolean isAutoCommit() {
    return autoCommit;
  }

  public void setAutoCommit(boolean autoCommit) {
    this.autoCommit = autoCommit;
  }

  public String getWebhookUrl() {
    return webhookUrl;
  }

  public void setWebhookUrl(String webhookUrl) {
    this.webhookUrl = webhookUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }
}

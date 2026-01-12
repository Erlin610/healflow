package com.healflow.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "incidents")
public class IncidentEntity {

  @Id
  @Column(nullable = false, updatable = false, length = 128)
  private String id;

  @Column(nullable = false, length = 128)
  private String appId;

  @Column(length = 512)
  private String repoUrl;

  @Column(length = 128)
  private String branch;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private IncidentStatus status;

  @Column(length = 256)
  private String sessionId;

  @Column(length = 256)
  private String containerName;

  @Column(length = 256)
  private String errorType;

  @Lob
  @Column
  private String errorMessage;

  @Lob
  @Column
  private String stackTrace;

  @Lob
  @Column
  private String analysisResult;

  @Lob
  @Column
  private String fixProposal;

  @Column(length = 64)
  private String commitId;

  @Column(columnDefinition = "TEXT")
  private String commitMessage;

  @Column(columnDefinition = "TEXT")
  private String changedFiles;

  @Column(columnDefinition = "TEXT")
  private String gitDiff;

  @Column(nullable = false, updatable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  @Version private long version;

  protected IncidentEntity() {}

  public IncidentEntity(String id, String appId, IncidentStatus status) {
    this.id = requireNonBlank(id, "id");
    this.appId = requireNonBlank(appId, "appId");
    this.status = status == null ? IncidentStatus.WAITING : status;
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

  public String getId() {
    return id;
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = requireNonBlank(appId, "appId");
  }

  public String getRepoUrl() {
    return repoUrl;
  }

  public void setRepoUrl(String repoUrl) {
    this.repoUrl = repoUrl;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public IncidentStatus getStatus() {
    return status;
  }

  public void setStatus(IncidentStatus status) {
    if (status == null) {
      throw new IllegalArgumentException("status must not be null");
    }
    this.status = status;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getContainerName() {
    return containerName;
  }

  public void setContainerName(String containerName) {
    this.containerName = containerName;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  public String getAnalysisResult() {
    return analysisResult;
  }

  public void setAnalysisResult(String analysisResult) {
    this.analysisResult = analysisResult;
  }

  public String getFixProposal() {
    return fixProposal;
  }

  public void setFixProposal(String fixProposal) {
    this.fixProposal = fixProposal;
  }

  public String getCommitId() {
    return commitId;
  }

  public void setCommitId(String commitId) {
    this.commitId = commitId;
  }

  public String getCommitMessage() {
    return commitMessage;
  }

  public void setCommitMessage(String commitMessage) {
    this.commitMessage = commitMessage;
  }

  public String getChangedFiles() {
    return changedFiles;
  }

  public void setChangedFiles(String changedFiles) {
    this.changedFiles = changedFiles;
  }

  public String getGitDiff() {
    return gitDiff;
  }

  public void setGitDiff(String gitDiff) {
    this.gitDiff = gitDiff;
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

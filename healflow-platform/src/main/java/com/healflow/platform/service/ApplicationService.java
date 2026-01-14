package com.healflow.platform.service;

import com.healflow.platform.entity.ApplicationEntity;
import com.healflow.platform.repository.ApplicationRepository;
import com.healflow.platform.util.EncryptionUtil;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {

  private static final String MASKED_SECRET = "****";

  private final ApplicationRepository repository;
  private final EncryptionUtil encryptionUtil;

  public ApplicationService(ApplicationRepository repository, EncryptionUtil encryptionUtil) {
    this.repository = repository;
    this.encryptionUtil = encryptionUtil;
  }

  public ApplicationResponse create(ApplicationRequest request) {
    requireRequest(request);
    String appName = requireNonBlank(request.appName(), "appName");
    if (repository.existsById(appName)) {
      throw new IllegalStateException("Application already exists: " + appName);
    }
    ApplicationEntity entity = new ApplicationEntity(appName);
    applyUpdate(entity, request);
    return toResponse(repository.save(entity));
  }

  public ApplicationResponse update(String appName, ApplicationRequest request) {
    requireRequest(request);
    requireNonBlank(appName, "appName");
    if (request.appName() != null && !request.appName().equals(appName)) {
      throw new IllegalArgumentException("appName cannot be changed");
    }
    ApplicationEntity entity = findByName(appName);
    applyUpdate(entity, request);
    return toResponse(repository.save(entity));
  }

  public ApplicationResponse getApplication(String appName) {
    return toResponse(findByName(appName));
  }

  public List<ApplicationResponse> listApplications() {
    return repository.findAll().stream().map(this::toResponse).toList();
  }

  public ApplicationSecrets getApplicationSecrets(String appName) {
    ApplicationEntity entity = findByName(appName);
    return new ApplicationSecrets(
        decryptOrNull(entity.getGitToken()),
        decryptOrNull(entity.getAiApiKey()));
  }

  public boolean delete(String appName) {
    requireNonBlank(appName, "appName");
    if (!repository.existsById(appName)) {
      return false;
    }
    repository.deleteById(appName);
    return true;
  }

  private ApplicationEntity findByName(String appName) {
    requireNonBlank(appName, "appName");
    return repository
        .findById(appName)
        .orElseThrow(() -> new IllegalArgumentException("Application not found: " + appName));
  }

  private void applyUpdate(ApplicationEntity entity, ApplicationRequest request) {
    if (request.gitUrl() != null) {
      entity.setGitUrl(normalize(request.gitUrl()));
    }
    if (request.gitBranch() != null) {
      entity.setGitBranch(normalize(request.gitBranch()));
    }
    if (request.gitToken() != null) {
      entity.setGitToken(encryptOrNull(request.gitToken()));
    }
    if (request.aiApiKey() != null) {
      entity.setAiApiKey(encryptOrNull(request.aiApiKey()));
    }
    if (request.autoAnalyze() != null) {
      entity.setAutoAnalyze(request.autoAnalyze());
    }
    if (request.autoFixProposal() != null) {
      entity.setAutoFixProposal(request.autoFixProposal());
    }
    if (request.autoCommit() != null) {
      entity.setAutoCommit(request.autoCommit());
    }
    if (request.webhookUrl() != null) {
      entity.setWebhookUrl(normalize(request.webhookUrl()));
    }
  }

  private ApplicationResponse toResponse(ApplicationEntity entity) {
    return new ApplicationResponse(
        entity.getAppName(),
        entity.getGitUrl(),
        entity.getGitBranch(),
        mask(entity.getGitToken()),
        mask(entity.getAiApiKey()),
        entity.isAutoAnalyze(),
        entity.isAutoFixProposal(),
        entity.isAutoCommit(),
        entity.getWebhookUrl());
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String encryptOrNull(String value) {
    String normalized = normalize(value);
    return normalized == null ? null : encryptionUtil.encrypt(normalized);
  }

  private String decryptOrNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return encryptionUtil.decrypt(value);
  }

  private static String mask(String encryptedValue) {
    if (encryptedValue == null || encryptedValue.isBlank()) {
      return null;
    }
    return MASKED_SECRET;
  }

  private static void requireRequest(ApplicationRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request must not be null");
    }
  }

  private static String requireNonBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
    return value;
  }

  public record ApplicationRequest(
      String appName,
      String gitUrl,
      String gitBranch,
      String gitToken,
      String aiApiKey,
      Boolean autoAnalyze,
      Boolean autoFixProposal,
      Boolean autoCommit,
      String webhookUrl) {}

  public record ApplicationResponse(
      String appName,
      String gitUrl,
      String gitBranch,
      String gitToken,
      String aiApiKey,
      boolean autoAnalyze,
      boolean autoFixProposal,
      boolean autoCommit,
      String webhookUrl) {}

  public record ApplicationSecrets(String gitToken, String aiApiKey) {}
}

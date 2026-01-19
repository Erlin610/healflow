package com.healflow.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.common.dto.AnalysisResult;
import com.healflow.common.dto.FixProposal;
import com.healflow.common.dto.FixResult;
import com.healflow.common.dto.IncidentReport;
import com.healflow.common.enums.IncidentStatus;
import com.healflow.engine.dto.CommitInfo;
import com.healflow.engine.git.JGitManager;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.engine.sandbox.SandboxException;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.ShellTimeoutException;
import com.healflow.platform.dto.WebhookPayload;
import com.healflow.platform.entity.ErrorFingerprintEntity;
import com.healflow.platform.entity.IncidentEntity;
import com.healflow.platform.repository.IncidentRepository;
import java.io.File;
import java.time.Instant;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class IncidentService {

  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
  private static final String MOCK_AGENT_SCRIPT_NAME = "mock-agent.sh";
  private static final String CONTAINER_WORKSPACE = "/src";
  private static final String CONTAINER_SCRIPT_PATH = CONTAINER_WORKSPACE + "/" + MOCK_AGENT_SCRIPT_NAME;
  private static final Duration MOCK_AGENT_TIMEOUT = Duration.ofSeconds(60);
  private static final List<IncidentStatus> ANALYZED_STATUSES =
      List.of(IncidentStatus.PENDING_REVIEW, IncidentStatus.FIXED, IncidentStatus.IGNORED);

  private final GitWorkspaceManager gitManager;
  private final DockerSandboxManager dockerSandboxManager;
  private final IncidentRepository incidentRepository;
  private final FingerprintService fingerprintService;
  private final ApplicationService applicationService;
  private final String sandboxImage;
  private final String aiAgentImage;
  private final ObjectMapper objectMapper;
  private final JGitManager jGitManager;
  private ApplicationContext applicationContext;
  private WebhookService webhookService;

  public IncidentService(
      GitWorkspaceManager gitManager,
      DockerSandboxManager dockerSandboxManager,
      IncidentRepository incidentRepository,
      FingerprintService fingerprintService,
      ApplicationService applicationService,
      @Value("${healflow.sandbox.image:ubuntu:latest}") String sandboxImage,
      @Value("${healflow.ai.image:healflow-agent:v1}") String aiAgentImage,
      @Value("${healflow.git.token:}") String gitToken) {
    this.gitManager = gitManager;
    this.dockerSandboxManager = dockerSandboxManager;
    this.incidentRepository = incidentRepository;
    this.fingerprintService = fingerprintService;
    this.applicationService = applicationService;
    this.sandboxImage = sandboxImage;
    this.aiAgentImage = aiAgentImage;
    this.objectMapper = new ObjectMapper();
    this.jGitManager = new JGitManager(gitToken);
  }

  @Autowired
  void setApplicationContext(ApplicationContext applicationContext) {
    this.applicationContext = applicationContext;
  }

  @Autowired(required = false)
  void setWebhookService(WebhookService webhookService) {
    this.webhookService = webhookService;
  }

  @Transactional
  public String createIncident(IncidentReport report) {
    log.info("Creating incident for app: {}", report.appId());

    // Auto-register application if not exists
    ensureApplicationExists(report);

    String incidentId = "inc-" + System.currentTimeMillis();
    IncidentEntity incident = new IncidentEntity(incidentId, report.appId(), IncidentStatus.OPEN);
    incident.setRepoUrl(report.repoUrl());
    incident.setBranch(report.branch());
    incident.setErrorType(report.errorType());
    incident.setErrorMessage(report.errorMessage());
    incident.setStackTrace(report.stackTrace());

    ErrorFingerprintEntity fingerprint = fingerprintService.recordOccurrence(
        report.errorType(), report.stackTrace());
    incident.setFingerprintId(fingerprint.getFingerprint());

    incidentRepository.save(incident);
    notifyWebhook(incident, report);
    log.info("Incident created with ID: {}, fingerprint: {}, occurrenceCount: {}",
        incidentId, fingerprint.getFingerprint(), fingerprint.getOccurrenceCount());

    // Trigger auto-analysis if enabled
    triggerAutoAnalysisIfEnabledAfterCommit(incident, report);

    return incidentId;
  }

  private void triggerAutoAnalysisIfEnabledAfterCommit(IncidentEntity incident, IncidentReport report) {
    if (applicationContext == null) {
      // Tests may instantiate IncidentService directly; keep behavior reasonable.
      triggerAutoAnalysisIfEnabled(incident, report);
      return;
    }

    IncidentService self = applicationContext.getBean(IncidentService.class);
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      self.triggerAutoAnalysisIfEnabled(incident, report);
      return;
    }

    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            try {
              self.triggerAutoAnalysisIfEnabled(incident, report);
            } catch (Exception e) {
              log.error("Failed to trigger auto-analysis for incident: {}", incident.getId(), e);
            }
          }
        });
  }

  @Async
  @Transactional
  public void triggerAutoAnalysisIfEnabled(IncidentEntity incident, IncidentReport report) {
    if (incident == null || report == null) {
      return;
    }

    String incidentId = incident.getId();
    String appId = incident.getAppId();
    String fingerprintId = incident.getFingerprintId();

    try {
      ApplicationService.ApplicationResponse app = applicationService.getApplication(appId);
      if (!app.autoAnalyze()) {
        log.debug("Auto-analyze disabled for app: {}", appId);
        return;
      }

      ZoneId serverZone = ZoneId.systemDefault();
      Instant startOfToday = LocalDate.now(serverZone).atStartOfDay(serverZone).toInstant();
      if (incidentRepository.existsByFingerprintIdAndStatusInAndCreatedAtGreaterThanEqual(
          fingerprintId, ANALYZED_STATUSES, startOfToday)) {
        log.info("Fingerprint {} already analyzed today, skipping auto-analysis for incident {}",
            fingerprintId, incidentId);
        incidentRepository.findById(incidentId).ifPresent(current -> {
          if (current.getStatus() == IncidentStatus.OPEN) {
            current.setStatus(IncidentStatus.SKIP);
            incidentRepository.save(current);
          }
        });
        return;
      }

      Optional<IncidentEntity> analyzingIncident = incidentRepository
          .findFirstByFingerprintIdAndStatusWithLock(fingerprintId, IncidentStatus.ANALYZING);

      if (analyzingIncident.isPresent()) {
        log.info("Fingerprint {} already being analyzed by incident {}, marking current as ANALYZING",
            fingerprintId, analyzingIncident.get().getId());
        incidentRepository.findById(incidentId).ifPresent(current -> {
          current.setStatus(IncidentStatus.ANALYZING);
          incidentRepository.save(current);
        });
        return;
      }

      log.info("Triggering auto-analysis for incident: {}", incidentId);
      if (applicationContext == null) {
        analyzeIncident(incidentId, report);
        return;
      }
      applicationContext.getBean(IncidentService.class).startAutoAnalysis(incidentId, report);
    } catch (Exception e) {
      log.error("Failed to trigger auto-analysis for incident: {}", incidentId, e);
    }
  }

  @Async
  public void startAutoAnalysis(String incidentId, IncidentReport report) {
    try {
      analyzeIncident(incidentId, report);
    } catch (Exception e) {
      log.error("Failed to trigger auto-analysis for incident: {}", incidentId, e);
    }
  }

  private void ensureApplicationExists(IncidentReport report) {
    try {
      applicationService.getApplication(report.appId());
      log.debug("Application already exists: {}", report.appId());
    } catch (IllegalArgumentException e) {
      // Application not found, auto-register
      log.info("Auto-registering application: {}", report.appId());
      ApplicationService.ApplicationRequest request = new ApplicationService.ApplicationRequest(
          report.appId(),
          report.repoUrl(),
          report.branch(),
          null, // gitToken - to be configured later
          null, // aiApiKey - to be configured later
          false, // autoAnalyze - disabled by default
          false, // autoFixProposal - disabled by default
          false, // autoCommit - disabled by default
          null  // webhookUrl - to be configured later
      );
      applicationService.create(request);
      log.info("Application auto-registered: {}", report.appId());
    }
  }

  private static String buildContainerName(String appId) {
    String safeAppId = sanitizeContainerNameComponent(appId);
    // 一个 appId 对应一个固定容器，不使用时间戳，永久复用
    return "healflow-sandbox-" + safeAppId;
  }

  private static String sanitizeContainerNameComponent(String value) {
    if (value == null || value.isBlank()) {
      return "app";
    }
    String trimmed = value.trim();
    StringBuilder builder = new StringBuilder(trimmed.length());
    for (int i = 0; i < trimmed.length(); i++) {
      char current = trimmed.charAt(i);
      if ((current >= 'a' && current <= 'z')
          || (current >= 'A' && current <= 'Z')
          || (current >= '0' && current <= '9')
          || current == '_'
          || current == '.'
          || current == '-') {
        builder.append(current);
      } else {
        builder.append('-');
      }
    }
    if (builder.length() == 0) {
      return "app";
    }
    return builder.toString();
  }

  // ========== Phase 5: Multi-stage AI Analysis ==========

  public AnalysisResult analyzeIncident(String incidentId, IncidentReport report) {
    IncidentEntity incident = findOrCreateIncident(incidentId, report);
    transitionOrThrow(incident, IncidentStatus.ANALYZING);
    incident = incidentRepository.saveAndFlush(incident);

    try {
      // 生成容器名并传递给分析方法
      String containerName = buildContainerName(report.appId());
      AnalysisResult result = analyzeIncident(report, containerName);
      incident.setSessionId(result.sessionId());
      incident.setAnalysisResult(result.structuredOutput());
      transitionOrThrow(incident, IncidentStatus.PENDING_REVIEW);
      incident.setContainerName(containerName);
      incidentRepository.save(incident);
      
      // Share analysis result with all incidents of same fingerprint
      shareAnalysisResultWithSameFingerprint(incident);

      if (webhookService != null) {
        try {
          webhookService.notifyAnalysisComplete(incident);
        } catch (Exception e) {
          log.warn("Webhook notification failed for analysis completion {}", incident.getId(), e);
        }
      }
       
      return result;
    } catch (RuntimeException e) {
      resetIncidentStatus(incident.getId(), IncidentStatus.OPEN);
      // Reset all same fingerprint incidents back to OPEN
      resetSameFingerprintIncidents(incident.getFingerprintId(), IncidentStatus.OPEN);
      throw e;
    }
  }

  private void shareAnalysisResultWithSameFingerprint(IncidentEntity sourceIncident) {
    if (sourceIncident == null) {
      return;
    }

    String fingerprintId = sourceIncident.getFingerprintId();
    if (fingerprintId == null || fingerprintId.isBlank()) {
      return;
    }

    String sourceErrorType = sourceIncident.getErrorType();
    List<IncidentEntity> sameFingerprint = incidentRepository.findByFingerprintId(fingerprintId);

    for (IncidentEntity incident : sameFingerprint) {
      if (incident.getStatus() != IncidentStatus.ANALYZING) {
        continue;
      }
      if (incident.getId().equals(sourceIncident.getId())) {
        continue;
      }

      if (!Objects.equals(sourceErrorType, incident.getErrorType())) {
        log.warn(
            "Refusing to share analysis across different errorType (fingerprintId={}, from={}({}) to={}({}))",
            fingerprintId,
            sourceIncident.getId(),
            sourceErrorType,
            incident.getId(),
            incident.getErrorType());
        // Recovery path: do not keep it stuck in ANALYZING.
        incident.setStatus(IncidentStatus.OPEN);
        incidentRepository.save(incident);
        continue;
      }

      incident.setSessionId(sourceIncident.getSessionId());
      incident.setAnalysisResult(sourceIncident.getAnalysisResult());
      incident.setContainerName(sourceIncident.getContainerName());
      transitionOrThrow(incident, IncidentStatus.PENDING_REVIEW);
      incidentRepository.save(incident);
      log.info("Shared analysis result from {} to {}", sourceIncident.getId(), incident.getId());
    }
  }

  private void resetSameFingerprintIncidents(String fingerprintId, IncidentStatus status) {
    List<IncidentEntity> incidents = incidentRepository.findByFingerprintId(fingerprintId);
    for (IncidentEntity incident : incidents) {
      if (incident.getStatus() == IncidentStatus.ANALYZING) {
        incident.setStatus(status);
        incidentRepository.save(incident);
        log.info("Reset incident {} to status {}", incident.getId(), status);
      }
    }
  }

  public FixProposal generateFix(String incidentId) {
    IncidentEntity incident = loadIncidentOrThrow(incidentId);
    if (incident.getStatus() != IncidentStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Incident is not PENDING_REVIEW");
    }
    if (incident.getSessionId() == null || incident.getSessionId().isBlank()) {
      throw new IllegalStateException("Incident has no sessionId");
    }

    Path workspace = prepareWorkspaceOrThrow(incident);
    try {
      FixProposal proposal = generateFix(incident.getSessionId(), workspace);
      incident.setFixProposal(proposal.structuredOutput());
      incidentRepository.save(incident);
      return proposal;
    } catch (RuntimeException e) {
      resetIncidentStatus(incident.getId(), IncidentStatus.PENDING_REVIEW);
      throw e;
    }
  }

  public FixResult applyFix(String incidentId) {
    IncidentEntity incident = loadIncidentOrThrow(incidentId);
    if (incident.getStatus() != IncidentStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Incident is not PENDING_REVIEW");
    }
    if (incident.getSessionId() == null || incident.getSessionId().isBlank()) {
      throw new IllegalStateException("Incident has no sessionId");
    }

    Path workspace = prepareWorkspaceOrThrow(incident);
    try {
      FixResult result = applyFix(incident.getSessionId(), workspace);
      transitionOrThrow(incident, IncidentStatus.FIXED);
      incidentRepository.save(incident);
      return result;
    } catch (RuntimeException e) {
      resetIncidentStatus(incident.getId(), IncidentStatus.PENDING_REVIEW);
      throw e;
    }
  }

  private IncidentEntity findOrCreateIncident(String incidentId, IncidentReport report) {
    if (incidentId == null || incidentId.isBlank()) {
      throw new IllegalArgumentException("incidentId must not be blank");
    }
    if (report == null) {
      throw new IllegalArgumentException("report must not be null");
    }

    Optional<IncidentEntity> existing = incidentRepository.findById(incidentId);
    if (existing.isPresent()) {
      IncidentEntity incident = existing.get();
      incident.setAppId(report.appId());
      incident.setRepoUrl(report.repoUrl());
      incident.setBranch(report.branch());
      if (incident.getStatus() == null) {
        incident.setStatus(IncidentStatus.OPEN);
      } else if (incident.getStatus() == IncidentStatus.FIXED) {
        transitionOrThrow(incident, IncidentStatus.REGRESSION);
        notifyWebhook(incident, report);
      }
      return incident;
    }

    IncidentEntity created = new IncidentEntity(incidentId, report.appId(), IncidentStatus.OPEN);
    created.setRepoUrl(report.repoUrl());
    created.setBranch(report.branch());
    return created;
  }

  private void notifyWebhook(IncidentEntity incident, IncidentReport report) {
    if (webhookService == null || incident == null || report == null) {
      return;
    }
    try {
      WebhookPayload payload =
          new WebhookPayload(
              incident.getAppId(),
              incident.getId(),
              incident.getStatus(),
              report.errorType(),
              report.errorMessage(),
              report.occurredAt(),
              null);
      webhookService.notifyIncident(payload);
    } catch (Exception e) {
      log.warn("Webhook notification failed for incident {}", incident.getId(), e);
    }
  }

  private IncidentEntity loadIncidentOrThrow(String incidentId) {
    if (incidentId == null || incidentId.isBlank()) {
      throw new IllegalArgumentException("incidentId must not be blank");
    }
    return incidentRepository
        .findById(incidentId)
        .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));
  }

  private Path prepareWorkspaceOrThrow(IncidentEntity incident) {
    if (incident.getAppId() == null || incident.getAppId().isBlank()) {
      throw new IllegalStateException("Incident has no appId");
    }
    
    // Get Git URL and branch from Application configuration
    ApplicationService.ApplicationResponse app;
    try {
      app = applicationService.getApplication(incident.getAppId());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Application not found: " + incident.getAppId(), e);
    }
    
    String repoUrl = app.gitUrl();
    String branch = app.gitBranch();
    
    if (repoUrl == null || repoUrl.isBlank()) {
      throw new IllegalStateException("Application has no gitUrl configured: " + incident.getAppId());
    }
    if (branch == null || branch.isBlank()) {
      branch = "main"; // Default branch
    }
    
    return gitManager.prepareWorkspace(incident.getAppId(), repoUrl, branch);
  }

  private void transitionOrThrow(IncidentEntity incident, IncidentStatus target) {
    IncidentStatus current = incident.getStatus();
    if (current == null) {
      incident.setStatus(IncidentStatus.OPEN);
      current = IncidentStatus.OPEN;
    }
    if (!current.canTransitionTo(target)) {
      throw new IllegalStateException("Invalid transition: " + current + " -> " + target);
    }
    incident.setStatus(target);
  }

  private void resetIncidentStatus(String incidentId, IncidentStatus fallbackStatus) {
    try {
      IncidentEntity incident = loadIncidentOrThrow(incidentId);
      if (fallbackStatus != null && incident.getStatus() != fallbackStatus) {
        incident.setStatus(fallbackStatus);
        incidentRepository.save(incident);
      }
    } catch (Exception ignored) {
      // Best-effort only.
    }
  }

  public List<Map<String, Object>> listIncidents(String statusFilter) {
    List<IncidentEntity> incidents;
    if (statusFilter != null && !statusFilter.isBlank()) {
      try {
        IncidentStatus status = IncidentStatus.valueOf(statusFilter.toUpperCase());
        incidents = incidentRepository.findByStatusOrderByCreatedAtDesc(status);
      } catch (IllegalArgumentException e) {
        incidents = incidentRepository.findAllByOrderByCreatedAtDesc();
      }
    } else {
      incidents = incidentRepository.findAllByOrderByCreatedAtDesc();
    }

    return incidents.stream()
        .map(this::toMap)
        .toList();
  }

  @Transactional
  public long deleteAllIncidents() {
    long count = incidentRepository.count();
    if (count == 0) {
      return 0;
    }

    incidentRepository.deleteAllInBatch();
    return count;
  }

  public Map<String, Object> getIncidentDetails(String incidentId) {
    IncidentEntity incident = loadIncidentOrThrow(incidentId);
    return toMap(incident);
  }

  private Map<String, Object> toMap(IncidentEntity incident) {
    Map<String, Object> map = new java.util.HashMap<>();
    map.put("id", incident.getId());
    map.put("appId", incident.getAppId());
    map.put("repoUrl", incident.getRepoUrl());
    map.put("branch", incident.getBranch());
    map.put("status", incident.getStatus().name());
    map.put("sessionId", incident.getSessionId());
    map.put("errorType", incident.getErrorType());
    map.put("errorMessage", incident.getErrorMessage());
    map.put("stackTrace", incident.getStackTrace());
    map.put("analysisResult", incident.getAnalysisResult());
    map.put("fixProposal", incident.getFixProposal());
    map.put("createdAt", incident.getCreatedAt());
    map.put("updatedAt", incident.getUpdatedAt());
    map.put("statusChangedAt", incident.getStatusChangedAt());
    map.put("fingerprintId", incident.getFingerprintId());
    if (incident.getFingerprintId() != null) {
      fingerprintService.findByFingerprint(incident.getFingerprintId())
          .ifPresent(fp -> map.put("occurrenceCount", fp.getOccurrenceCount()));
    }
    return map;
  }

  public AnalysisResult analyzeIncident(IncidentReport report, String containerName) {
    log.info("Phase 5 Stage 1: Analyzing incident for {} with container {}", report.appId(), containerName);

    try {
      Path sourceCodePath = gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch());

      String prompt = String.format(
          "请用中文分析这个 Java 应用错误：\n" +
          "错误类型: %s\n" +
          "错误信息: %s\n" +
          "堆栈跟踪:\n%s\n\n" +
          "请提供详细的分析和修复方案建议，但不要直接修改代码。\n" +
          "如果有多种修复方案，请在 solutions 字段中列举所有可行方案，每个方案包含 title、description 和 recommended（是否推荐）。\n" +
          "如果需要用户确认某些信息才能确定最佳修复方案，请在 questions 字段中提出问题。\n" +
          "问题格式参考 Claude Code 的 AskUserQuestion 工具：\n" +
          "- question: 问题描述\n" +
          "- header: 简短标签（最多12字符）\n" +
          "- options: 2-4个选项，每个包含 label 和 description\n" +
          "- multiSelect: 是否允许多选（true/false）\n" +
          "请用中文回复。",
          report.errorType(),
          report.errorMessage(),
          truncate(report.stackTrace(), 1000)
      );

      String jsonSchema = "{\"type\":\"object\",\"properties\":{\"bug_type\":{\"type\":\"string\"},\"severity\":{\"type\":\"string\",\"enum\":[\"critical\",\"high\",\"medium\",\"low\"]},\"root_cause\":{\"type\":\"string\"},\"affected_files\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"analysis\":{\"type\":\"string\"},\"solutions\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"},\"recommended\":{\"type\":\"boolean\"}},\"required\":[\"title\",\"description\"]}},\"confidence\":{\"type\":\"number\",\"minimum\":0,\"maximum\":1},\"questions\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"question\":{\"type\":\"string\"},\"header\":{\"type\":\"string\"},\"multiSelect\":{\"type\":\"boolean\"},\"options\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"label\":{\"type\":\"string\"},\"description\":{\"type\":\"string\"}},\"required\":[\"label\",\"description\"]}}},\"required\":[\"question\",\"header\",\"options\",\"multiSelect\"]}}},\"required\":[\"bug_type\",\"severity\",\"root_cause\",\"analysis\",\"confidence\"]}";

      String runId = Long.toString(System.nanoTime());
      String schemaFileName = "analysis-schema-" + runId + ".json";
      String scriptFileName = "analyze-incident-" + runId + ".sh";
      String logFileName = "analyze-incident-" + runId + ".log";

      Path schemaFile = sourceCodePath.resolve(schemaFileName);
      Path scriptFile = sourceCodePath.resolve(scriptFileName);
      Path logFile = sourceCodePath.resolve(logFileName);

      try {
        java.nio.file.Files.writeString(schemaFile, jsonSchema);
        String script = "#!/bin/sh\n" +
            "export IS_SANDBOX=1\n" +
            "export CLAUDE_CODE_DISABLE_COMMAND_INJECTION_CHECK=true\n" +
            "SCHEMA=$(cat /src/" + schemaFileName + ")\n" +
            "claude -p '" + prompt.replace("'", "'\\''") + "' " +
            "--allowedTools Read,Grep,Glob " +
            "--output-format json " +
            "--json-schema \"$SCHEMA\" > /src/" + logFileName + " 2>&1\n" +
            "echo \"Exit code: $?\" >> /src/" + logFileName + "\n";
        java.nio.file.Files.writeString(scriptFile, script);

        log.info("Executing Claude analysis in container: {}", containerName);

        log.info("=== Starting Analysis Execution ===");
        log.info("Container: {}", containerName);
        log.info("Workspace: {}", sourceCodePath);
        log.info("Script content:\n{}", script);
        log.info("Docker command: docker run/exec -w /src {} sh /src/{}", containerName, scriptFileName);
        log.info("Prompt: {}", prompt);

        CommandResult result = dockerSandboxManager.executeInteractiveRunInSandbox(
            containerName,
            sourceCodePath,
            CONTAINER_WORKSPACE,
            aiAgentImage,
            Map.of(),
            List.of("sh", "/src/" + scriptFileName),
            Duration.ofMinutes(30),
            List.of()
        );

        log.info("=== Analysis Execution Completed ===");
        log.info("Exit code: {}", result.exitCode());
        log.info("Output length: {} chars", result.output().length());

        // 从日志文件读取输出（因为使用了重定向而不是 tee）
        String rawOutput = "";
        if (java.nio.file.Files.exists(logFile)) {
          try {
            rawOutput = java.nio.file.Files.readString(logFile);
            log.info("=== Claude Code Log File Content ===\n{}", rawOutput);
          } catch (Exception e) {
            log.warn("Failed to read log file: {}", e.getMessage());
            rawOutput = result.output();
          }
        } else {
          rawOutput = result.output();
        }

        // 过滤掉警告信息，找到第一个 JSON 对象
        String jsonOutput = rawOutput;
        int jsonStart = rawOutput.indexOf('{');
        if (jsonStart > 0) {
          jsonOutput = rawOutput.substring(jsonStart);
          log.info("Filtered {} chars of non-JSON prefix", jsonStart);
        }

        // Claude Code 输出格式：对象（新）或数组（旧）
        JsonNode response = objectMapper.readTree(jsonOutput);
        String sessionId = response.path("session_id").asText();
        String structuredOutput = response.path("structured_output").toString();
        String fullText = response.path("result").asText();

        log.info("Analysis complete. Session ID: {}, Container: {}", sessionId, containerName);
        return new AnalysisResult(sessionId, structuredOutput, fullText);

      } finally {
        java.nio.file.Files.deleteIfExists(schemaFile);
        java.nio.file.Files.deleteIfExists(scriptFile);
        java.nio.file.Files.deleteIfExists(logFile);
      }

    } catch (Exception e) {
      log.error("Analysis failed", e);
      throw new RuntimeException("Analysis failed", e);
    }
  }

  public FixProposal generateFix(String sessionId, Path sourceCodePath) {
    log.info("Phase 5 Stage 2: Generating fix for session {}", sessionId);

    try {
      String apiKey = getApiKeyFromHost();
      if (apiKey == null || apiKey.isBlank()) {
        throw new RuntimeException("No API Key found");
      }

      String prompt = "Based on the analysis, generate a detailed fix proposal. " +
                     "Show the code changes but DO NOT apply them yet.";

      String jsonSchema = "{\"type\":\"object\",\"properties\":{\"fix_description\":{\"type\":\"string\"},\"files_to_modify\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"code_changes\":{\"type\":\"string\"},\"test_strategy\":{\"type\":\"string\"},\"risk_level\":{\"type\":\"string\",\"enum\":[\"low\",\"medium\",\"high\"]}},\"required\":[\"fix_description\",\"files_to_modify\",\"code_changes\"]}";

      List<String> command = List.of(
          "sh", "-c",
          "printf '%s' '" + jsonSchema.replace("'", "'\\''") + "' > /tmp/schema-fix.json && " +
          "claude -p \"" + prompt.replace("\"", "\\\"") + "\" " +
          "--resume " + sessionId + " " +
          "--allowedTools Read " +
          "--output-format json " +
          "--json-schema \"$(cat /tmp/schema-fix.json)\""
      );

      String containerName = buildContainerName("fix-gen");
      CommandResult result = dockerSandboxManager.executeInteractiveRunInSandbox(
          containerName,
          sourceCodePath,
          CONTAINER_WORKSPACE,
          aiAgentImage,
          Map.of("ANTHROPIC_API_KEY", apiKey),
          command,
          Duration.ofMinutes(30),
          List.of()
      );

      JsonNode response = objectMapper.readTree(result.output());
      String structuredOutput = response.path("structured_output").toString();
      String fullText = response.path("result").asText();

      log.info("Fix proposal generated");
      return new FixProposal(sessionId, structuredOutput, fullText);

    } catch (Exception e) {
      log.error("Fix generation failed", e);
      throw new RuntimeException("Fix generation failed", e);
    }
  }

  public FixResult applyFix(String sessionId, Path sourceCodePath) {
    log.info("Phase 5 Stage 3: Applying fix for session {}", sessionId);

    try {
      String apiKey = getApiKeyFromHost();
      if (apiKey == null || apiKey.isBlank()) {
        throw new RuntimeException("No API Key found");
      }

      String prompt = "Apply the fix we discussed. " +
                     "Make the code changes and run tests to verify.";

      List<String> command = List.of(
          "claude", "-p", prompt,
          "--resume", sessionId,
          "--allowedTools", "Read,Edit,Write,Bash(mvn test:*)",
          "--output-format", "json"
      );

      String containerName = buildContainerName("fix-apply");
      CommandResult result = dockerSandboxManager.executeInteractiveRunInSandbox(
          containerName,
          sourceCodePath,
          CONTAINER_WORKSPACE,
          aiAgentImage,
          Map.of("ANTHROPIC_API_KEY", apiKey),
          command,
          Duration.ofMinutes(30),
          List.of()
      );

      JsonNode response = objectMapper.readTree(result.output());
      String resultText = response.path("result").asText();
      String usage = response.path("usage").toString();

      log.info("Fix applied successfully");
      return new FixResult(resultText, usage);

    } catch (Exception e) {
      log.error("Fix application failed", e);
      throw new RuntimeException("Fix application failed", e);
    }
  }

  private String getApiKeyFromHost() {
    String envApiKey = System.getenv("ANTHROPIC_API_KEY");
    if (envApiKey != null && !envApiKey.isBlank()) {
      return envApiKey;
    }

    String envAuthToken = System.getenv("ANTHROPIC_AUTH_TOKEN");
    if (envAuthToken != null && !envAuthToken.isBlank()) {
      return envAuthToken;
    }

    try {
      File settingsFile = new File(System.getProperty("user.home"), ".claude/settings.json");
      if (!settingsFile.exists()) {
        return null;
      }

      JsonNode root = objectMapper.readTree(settingsFile);
      for (String jsonPointer :
          new String[] {
            "/env/ANTHROPIC_AUTH_TOKEN",
            "/env/ANTHROPIC_API_KEY",
            "/env/anthropicApiKey",
            "/env/anthropic_api_key",
            "/ANTHROPIC_AUTH_TOKEN",
            "/ANTHROPIC_API_KEY",
            "/anthropicApiKey",
            "/anthropic_api_key",
            "/apiKey"
          }) {
        JsonNode candidate = root.at(jsonPointer);
        if (candidate.isTextual()) {
          String value = candidate.asText();
          if (value != null && !value.isBlank()) {
            return value;
          }
        }
      }
    } catch (Exception e) {
      log.error("Failed to read ~/.claude/settings.json", e);
    }

    return null;
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return "";
    return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
  }

  private void commitAndPushFixIfPossible(IncidentEntity incident, Path repositoryDirectory) {
    String incidentId = incident.getId();
    String branch = incident.getBranch();
    String errorType = incident.getErrorType();

    if (branch == null || branch.isBlank()) {
      log.warn("Skipping commit/push: missing branch for incident {}", incidentId);
      return;
    }
    if (errorType == null || errorType.isBlank()) {
      log.warn("Skipping commit/push: missing errorType for incident {}", incidentId);
      return;
    }

    String normalizedBranch = branch.trim();
    if (normalizedBranch.startsWith("origin/")) {
      normalizedBranch = normalizedBranch.substring("origin/".length());
    }

    try {
      Optional<CommitInfo> committed = jGitManager.commitFix(repositoryDirectory, incidentId, errorType);
      if (committed.isEmpty()) {
        log.info("No repository changes detected; skipping push for incident {}", incidentId);
        return;
      }
      CommitInfo commitInfo = committed.get();
      incident.setCommitId(commitInfo.commitId());
      incident.setCommitMessage(commitInfo.commitMessage());
      try {
        incident.setChangedFiles(objectMapper.writeValueAsString(commitInfo.changedFiles()));
        incident.setGitDiff(objectMapper.writeValueAsString(commitInfo.gitDiff()));
      } catch (JsonProcessingException e) {
        log.error("Failed to serialize commit info for incident {}", incidentId, e);
      }
      log.info("Committed fix for incident {} at {}", incidentId, commitInfo.commitId());
    } catch (RuntimeException e) {
      log.error("Git commit failed for incident {}", incidentId, e);
      return;
    }

    try {
      jGitManager.push(repositoryDirectory, normalizedBranch);
      log.info("Pushed fix for incident {} to branch {}", incidentId, normalizedBranch);
    } catch (RuntimeException e) {
      log.error("Git push failed for incident {} branch {}", incidentId, normalizedBranch, e);
    }
  }

  @Async
  public void startFixWithAnswers(String incidentId, Object answersObj, String additionalInfo) {
    log.info("Starting fix with user answers for incident: {}", incidentId);

    IncidentEntity incident = incidentRepository.findById(incidentId)
        .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

    if (incident.getStatus() != IncidentStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Incident must be in PENDING_REVIEW status");
    }

    try {
      Path sourceCodePath = gitManager.prepareWorkspace(
          incident.getAppId(), incident.getRepoUrl(), incident.getBranch());

      StringBuilder promptBuilder = new StringBuilder();
      promptBuilder.append("请修复这个错误。\n\n");
      promptBuilder.append("用户确认的信息: ").append(answersObj != null ? answersObj.toString() : "无").append("\n\n");

      if (additionalInfo != null && !additionalInfo.isBlank()) {
        promptBuilder.append("补充信息: ").append(additionalInfo).append("\n\n");
      }

      promptBuilder.append("请生成修复代码并应用。只修改必要的代码，不要过度修改。请用中文回复。");

      String prompt = promptBuilder.toString();

      Path scriptFile = sourceCodePath.resolve("fix-incident.sh");
      Path logFile = sourceCodePath.resolve("fix-incident.log");
      String script = "#!/bin/sh\n" +
          "export IS_SANDBOX=1\n" +
          "export CLAUDE_CODE_DISABLE_COMMAND_INJECTION_CHECK=true\n" +
          "claude --dangerously-skip-permissions --resume " + incident.getSessionId() + " '" +
          prompt.replace("'", "'\\''") + "' > /src/fix-incident.log 2>&1\n" +
          "echo \"Exit code: $?\" >> /src/fix-incident.log\n";
      java.nio.file.Files.writeString(scriptFile, script);

      log.info("=== Starting Fix Execution ===");
      // 复用分析阶段的容器名，确保 session 数据可用
      String containerName = incident.getContainerName();
      if (containerName == null || containerName.isBlank()) {
        throw new IllegalStateException("Container name not found for incident: " + incidentId);
      }
      log.info("Container: {} (reusing from analysis)", containerName);
      log.info("Session ID: {}", incident.getSessionId());
      log.info("Workspace: {}", sourceCodePath);
      log.info("Script content:\n{}", script);
      log.info("Docker command: docker exec -w /src {} sh /src/fix-incident.sh", containerName);
      log.info("Prompt: {}", prompt);

      // 脚本已使用 --dangerously-skip-permissions 和环境变量跳过交互，不需要 autoApprovalRules
      // 传入空列表避免使用 docker exec -i，防止交互模式导致卡住
      CommandResult result = dockerSandboxManager.executeInteractiveRunInSandbox(
          containerName,
          sourceCodePath,
          CONTAINER_WORKSPACE,
          aiAgentImage,
          Map.of(),
          List.of("sh", "/src/fix-incident.sh"),
          Duration.ofMinutes(30),
          List.of()  // 空列表，不使用交互模式
      );

      log.info("=== Fix Execution Completed ===");
      log.info("Exit code: {}", result.exitCode());
      log.info("Output length: {} chars", result.output().length());

      // 从日志文件读取输出（因为使用了重定向而不是 tee）
      String rawOutput = "";
      if (java.nio.file.Files.exists(logFile)) {
        try {
          rawOutput = java.nio.file.Files.readString(logFile);
          log.info("=== Claude Code Log File Content ===\n{}", rawOutput);
        } catch (Exception e) {
          log.warn("Failed to read log file: {}", e.getMessage());
          rawOutput = result.output();
        }
      } else {
        rawOutput = result.output();
      }

      log.info("Output (first 2000 chars):\n{}", truncate(rawOutput, 2000));
      if (rawOutput.length() > 2000) {
        log.info("Output (last 1000 chars):\n{}", rawOutput.substring(Math.max(0, rawOutput.length() - 1000)));
      }

      // 检查是否成功（exit code 0 表示成功）
      boolean fixSuccessful = result.exitCode() == 0;
      log.info("Fix execution result: {}", fixSuccessful ? "SUCCESS" : "FAILED");

      java.nio.file.Files.deleteIfExists(scriptFile);

      // 重新加载实体，避免乐观锁冲突
      incident = incidentRepository.findById(incidentId)
          .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

      if (fixSuccessful) {
        commitAndPushFixIfPossible(incident, sourceCodePath);
        incident.setStatus(IncidentStatus.FIXED);
        incident.setFixProposal("AI修复完成\n\n" + rawOutput);
        log.info("✓ Fix completed successfully for incident: {}", incidentId);
      } else {
        incident.setStatus(IncidentStatus.PENDING_REVIEW);
        incident.setFixProposal("AI修复失败\n\n" + rawOutput);
        log.error("✗ Fix failed for incident: {}", incidentId);
      }

      incidentRepository.save(incident);
      log.info("Incident status updated to: {}", incident.getStatus());

    } catch (Exception e) {
      log.error("Fix failed for incident: {}", incidentId, e);
      incident.setStatus(IncidentStatus.PENDING_REVIEW);
      incidentRepository.save(incident);
    }
  }

  public void markNoActionNeeded(String incidentId) {
    log.info("Marking incident as no action needed: {}", incidentId);

    IncidentEntity incident = incidentRepository.findById(incidentId)
        .orElseThrow(() -> new IllegalArgumentException("Incident not found: " + incidentId));

    if (incident.getStatus() != IncidentStatus.PENDING_REVIEW) {
      throw new IllegalStateException("Incident must be in PENDING_REVIEW status");
    }

    transitionOrThrow(incident, IncidentStatus.IGNORED);
    incidentRepository.save(incident);
  }
}

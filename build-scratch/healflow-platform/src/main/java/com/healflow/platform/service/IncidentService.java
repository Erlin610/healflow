package com.healflow.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.common.dto.AnalysisResult;
import com.healflow.common.dto.FixProposal;
import com.healflow.common.dto.FixResult;
import com.healflow.common.dto.IncidentReport;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.engine.sandbox.SandboxException;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.ShellTimeoutException;
import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
  private static final String MOCK_AGENT_SCRIPT_NAME = "mock-agent.sh";
  private static final String CONTAINER_WORKSPACE = "/src";
  private static final String CONTAINER_SCRIPT_PATH = CONTAINER_WORKSPACE + "/" + MOCK_AGENT_SCRIPT_NAME;
  private static final Duration MOCK_AGENT_TIMEOUT = Duration.ofSeconds(60);

  private final GitWorkspaceManager gitManager;
  private final DockerSandboxManager dockerSandboxManager;
  private final String sandboxImage;
  private final String aiAgentImage;
  private final ObjectMapper objectMapper;

  public IncidentService(
      GitWorkspaceManager gitManager,
      DockerSandboxManager dockerSandboxManager,
      @Value("${healflow.sandbox.image:ubuntu:latest}") String sandboxImage,
      @Value("${healflow.ai.image:healflow-agent:v1}") String aiAgentImage) {
    this.gitManager = gitManager;
    this.dockerSandboxManager = dockerSandboxManager;
    this.sandboxImage = sandboxImage;
    this.aiAgentImage = aiAgentImage;
    this.objectMapper = new ObjectMapper();
  }

  @Async // key: async execution, non-blocking Controller response
  public void processIncident(IncidentReport report) {
    log.info("Start processing incident for {}", report.appId());

    try {
      // Phase 2: prepare source code
      Path sourceCodePath = gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch());

      log.info("Source code ready at: {}", sourceCodePath);

      Path scriptPath = sourceCodePath.resolve(MOCK_AGENT_SCRIPT_NAME);
      if (!Files.isRegularFile(scriptPath)) {
        log.error("Mock agent script not found at: {}", scriptPath);
        return;
      }

      // Phase 4: run mock agent in interactive Docker sandbox
      String containerName = buildContainerName(report.appId());
      Map<String, String> environment = report.environment() == null ? Map.of() : report.environment();
      CommandResult result =
          dockerSandboxManager.executeInteractiveRunInSandbox(
              containerName,
              sourceCodePath,
              CONTAINER_WORKSPACE,
              sandboxImage,
              environment,
              List.of("bash", CONTAINER_SCRIPT_PATH),
              MOCK_AGENT_TIMEOUT,
              List.of());
      log.info("Mock agent output:\n{}", result.output());
    } catch (SandboxException e) {
      Throwable cause = e.getCause();
      if (cause instanceof ShellTimeoutException timeout) {
        log.error("Mock agent timed out:\n{}", timeout.outputSoFar(), e);
        return;
      }
      log.error("Mock agent failed:\n{}", e.details(), e);
    } catch (Exception e) {
      log.error("Processing failed", e);
    }
  }

  private static String buildContainerName(String appId) {
    String safeAppId = sanitizeContainerNameComponent(appId);
    return "healflow-sandbox-" + safeAppId + "-" + Instant.now().toEpochMilli();
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

  public AnalysisResult analyzeIncident(IncidentReport report) {
    log.info("Phase 5 Stage 1: Analyzing incident for {}", report.appId());

    try {
      Path sourceCodePath = gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch());
      String apiKey = getApiKeyFromHost();
      if (apiKey == null || apiKey.isBlank()) {
        throw new RuntimeException("No API Key found");
      }

      String prompt = String.format(
          "Analyze this Java application error:\n" +
          "Error Type: %s\n" +
          "Error Message: %s\n" +
          "Stack Trace:\n%s\n\n" +
          "Provide detailed analysis but DO NOT generate fix yet.",
          report.errorType(),
          report.errorMessage(),
          truncate(report.stackTrace(), 1000)
      );

      String jsonSchema = """
          {
            "type": "object",
            "properties": {
              "bug_type": {"type": "string"},
              "severity": {"type": "string", "enum": ["critical", "high", "medium", "low"]},
              "root_cause": {"type": "string"},
              "affected_files": {"type": "array", "items": {"type": "string"}},
              "analysis": {"type": "string"},
              "confidence": {"type": "number", "minimum": 0, "maximum": 1}
            },
            "required": ["bug_type", "severity", "root_cause", "analysis", "confidence"]
          }
          """;

      List<String> command = List.of(
          "claude", "-p", prompt,
          "--allowedTools", "Read,Grep,Glob",
          "--output-format", "json",
          "--json-schema", jsonSchema
      );

      String containerName = buildContainerName(report.appId());
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
      String sessionId = response.path("session_id").asText();
      String structuredOutput = response.path("structured_output").toString();
      String fullText = response.path("result").asText();

      log.info("Analysis complete. Session ID: {}", sessionId);
      return new AnalysisResult(sessionId, structuredOutput, fullText);

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

      String jsonSchema = """
          {
            "type": "object",
            "properties": {
              "fix_description": {"type": "string"},
              "files_to_modify": {"type": "array", "items": {"type": "string"}},
              "code_changes": {"type": "string"},
              "test_strategy": {"type": "string"},
              "risk_level": {"type": "string", "enum": ["low", "medium", "high"]}
            },
            "required": ["fix_description", "files_to_modify", "code_changes"]
          }
          """;

      List<String> command = List.of(
          "claude", "-p", prompt,
          "--resume", sessionId,
          "--allowedTools", "Read",
          "--output-format", "json",
          "--json-schema", jsonSchema
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
}

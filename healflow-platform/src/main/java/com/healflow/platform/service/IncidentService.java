package com.healflow.platform.service;

import com.healflow.common.dto.IncidentReport;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.engine.shell.CommandResult;
import java.time.Instant;
import java.util.List;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class IncidentService {

  private static final Logger log = LoggerFactory.getLogger(IncidentService.class);
  private final GitWorkspaceManager gitManager;
  private final DockerSandboxManager dockerSandboxManager;
  private final String sandboxImage;

  public IncidentService(
      GitWorkspaceManager gitManager,
      DockerSandboxManager dockerSandboxManager,
      @Value("${healflow.sandbox.image:ubuntu:latest}") String sandboxImage) {
    this.gitManager = gitManager;
    this.dockerSandboxManager = dockerSandboxManager;
    this.sandboxImage = sandboxImage;
  }

  @Async // key: async execution, non-blocking Controller response
  public void processIncident(IncidentReport report) {
    log.info("Start processing incident for {}", report.appId());

    try {
      // Phase 2: prepare source code
      Path sourceCodePath = gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch());

      log.info("Source code ready at: {}", sourceCodePath);

      // Phase 3: start Docker sandbox for analysis
      String containerName = buildContainerName(report.appId());
      CommandResult result =
          dockerSandboxManager.executeInSandbox(
              containerName, sourceCodePath, "/src", sandboxImage, List.of("ls", "-al", "/src"));
      log.info("Sandbox (/src) output:\n{}", result.output());
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
}

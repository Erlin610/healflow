package com.healflow.starter.reporter;

import com.healflow.common.dto.IncidentReport;
import com.healflow.starter.config.HealFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

public class IncidentReporter {
  private static final Logger log = LoggerFactory.getLogger(IncidentReporter.class);

  private final HealFlowProperties properties;
  private final RestClient restClient;

  public IncidentReporter(HealFlowProperties properties) {
    this(properties, RestClient.builder().baseUrl(properties.getServerUrl()).build());
  }

  IncidentReporter(HealFlowProperties properties, RestClient restClient) {
    this.properties = properties;
    this.restClient = restClient;
  }

  public void report(Throwable ex) {
    if (!properties.isEnabled()) return;

    try {
      // 构造 DTO (这里暂时 mock commitId，下一步我们再加 Git 读取)
      IncidentReport report =
              new IncidentReport(
              properties.getAppId(),
              properties.getGitUrl(),
              defaultBranch(properties.getGitBranch()),
              ex.getClass().getName(),
              ex.getMessage(),
              getStackTraceAsString(ex),
              java.util.Collections.emptyMap(),
              java.time.Instant.now());

      // 发送
      restClient
          .post()
          .uri("/api/v1/incidents/report")
          .body(report)
          .retrieve()
          .toBodilessEntity();

      log.info("✅ HealFlow: Incident reported successfully.");
    } catch (Exception e) {
      log.warn("❌ HealFlow: Failed to report incident: {}", e.getMessage());
    }
  }

  private static String defaultBranch(String branch) {
    return (branch == null || branch.isBlank()) ? "main" : branch;
  }

  private String getStackTraceAsString(Throwable throwable) {
    java.io.StringWriter sw = new java.io.StringWriter();
    throwable.printStackTrace(new java.io.PrintWriter(sw));
    return sw.toString();
  }
}

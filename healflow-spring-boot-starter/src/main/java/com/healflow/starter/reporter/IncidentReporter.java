package com.healflow.starter.reporter;

import com.healflow.common.dto.IncidentReport;
import com.healflow.starter.config.HealFlowProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
      HttpContext httpContext = captureHttpContext();
      // 构造 DTO（Git 信息来自配置；HTTP 上下文尽力提取，非 HTTP 场景为 null）
      IncidentReport report =
              new IncidentReport(
              properties.getAppId(),
              properties.getGitUrl(),
              defaultBranch(properties.getGitBranch()),
              ex.getClass().getName(),
              ex.getMessage(),
              getStackTraceAsString(ex),
              httpContext.requestUrl(),
              httpContext.requestMethod(),
              httpContext.requestParams(),
              httpContext.traceId(),
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

  private static HttpContext captureHttpContext() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return HttpContext.empty();
    }

    String requestUrl = null;
    String requestMethod = null;
    String requestParams = null;
    String traceId = null;

    try {
      var request = servletAttributes.getRequest();
      if (request != null) {
        requestUrl = normalize(request.getRequestURL() != null ? request.getRequestURL().toString() : null);
        requestMethod = normalize(request.getMethod());
        requestParams = normalize(request.getQueryString());

        traceId = normalize(request.getHeader("X-Trace-Id"));
        if (traceId == null) {
          traceId = normalize(request.getHeader("traceId"));
        }
        if (traceId == null) {
          traceId = normalize(request.getHeader("X-B3-TraceId"));
        }
      }
    } catch (RuntimeException ignored) {
      // Best-effort only: incident reporting must not fail due to request extraction.
    }

    String mdcTraceId = normalize(MDC.get("traceId"));
    if (mdcTraceId != null) {
      traceId = mdcTraceId;
    }

    return new HttpContext(requestUrl, requestMethod, requestParams, traceId);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private record HttpContext(String requestUrl, String requestMethod, String requestParams, String traceId) {
    private static HttpContext empty() {
      return new HttpContext(null, null, null, null);
    }
  }
}

package com.healflow.starter.reporter;

import com.healflow.common.dto.IncidentReport;
import com.healflow.starter.config.HealFlowProperties;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public class IncidentReporter {
  private static final Logger log = LoggerFactory.getLogger(IncidentReporter.class);

  private final HealFlowProperties properties;
  private final RestTemplate restTemplate;

  public IncidentReporter(HealFlowProperties properties) {
    this(properties, new RestTemplate());
  }

  IncidentReporter(HealFlowProperties properties, RestTemplate restTemplate) {
    this.properties = Objects.requireNonNull(properties, "properties");
    this.restTemplate = Objects.requireNonNull(restTemplate, "restTemplate");
  }

  public void report(Throwable ex) {
    if (!properties.isEnabled()) {
      return;
    }

    try {
      HttpContext httpContext = captureHttpContext();
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
              Collections.<String, String>emptyMap(),
              Instant.now());

      restTemplate.postForEntity(
          joinUrl(properties.getServerUrl(), "/api/v1/incidents/report"), report, Void.class);

      log.info("HealFlow: Incident reported successfully.");
    } catch (Exception e) {
      log.warn("HealFlow: Failed to report incident: {}", e.getMessage());
    }
  }

  private static String defaultBranch(String branch) {
    return (branch == null || branch.trim().isEmpty()) ? "main" : branch;
  }

  private static String getStackTraceAsString(Throwable throwable) {
    StringWriter sw = new StringWriter();
    throwable.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }

  private static HttpContext captureHttpContext() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes)) {
      return HttpContext.empty();
    }
    ServletRequestAttributes servletAttributes = (ServletRequestAttributes) attributes;

    String requestUrl = null;
    String requestMethod = null;
    String requestParams = null;
    String traceId = null;

    try {
      HttpServletRequest request = servletAttributes.getRequest();
      if (request != null) {
        requestUrl =
            normalize(request.getRequestURL() != null ? request.getRequestURL().toString() : null);
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

  private static String joinUrl(String baseUrl, String path) {
    if (baseUrl == null) {
      return path;
    }
    if (path == null) {
      return baseUrl;
    }
    boolean baseEndsWithSlash = baseUrl.endsWith("/");
    boolean pathStartsWithSlash = path.startsWith("/");
    if (baseEndsWithSlash && pathStartsWithSlash) {
      return baseUrl + path.substring(1);
    }
    if (!baseEndsWithSlash && !pathStartsWithSlash) {
      return baseUrl + "/" + path;
    }
    return baseUrl + path;
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static final class HttpContext {

    private final String requestUrl;
    private final String requestMethod;
    private final String requestParams;
    private final String traceId;

    private HttpContext(String requestUrl, String requestMethod, String requestParams, String traceId) {
      this.requestUrl = requestUrl;
      this.requestMethod = requestMethod;
      this.requestParams = requestParams;
      this.traceId = traceId;
    }

    private static HttpContext empty() {
      return new HttpContext(null, null, null, null);
    }

    private String requestUrl() {
      return requestUrl;
    }

    private String requestMethod() {
      return requestMethod;
    }

    private String requestParams() {
      return requestParams;
    }

    private String traceId() {
      return traceId;
    }
  }
}


package com.healflow.platform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healflow.common.enums.IncidentStatus;
import com.healflow.platform.dto.WebhookPayload;
import com.healflow.platform.entity.ApplicationEntity;
import com.healflow.platform.entity.IncidentEntity;
import com.healflow.platform.repository.ApplicationRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

  private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
  private static final int DEFAULT_MAX_ATTEMPTS = 3;
  private static final long DEFAULT_BASE_BACKOFF_MILLIS = 200L;

  private final ApplicationRepository applicationRepository;
  private final ObjectMapper objectMapper;
  private final HttpSender httpSender;
  private final boolean notifyOnRegression;
  private final String platformBaseUrl;
  private final int maxAttempts;
  private final long baseBackoffMillis;

  @Autowired
  public WebhookService(
      ApplicationRepository applicationRepository,
      ObjectMapper objectMapper,
      @Value("${healflow.webhook.notify-on-regression:false}") boolean notifyOnRegression,
      @Value("${healflow.platform.base-url:}") String platformBaseUrl) {
    this(
        applicationRepository,
        objectMapper,
        null,
        notifyOnRegression,
        platformBaseUrl,
        DEFAULT_MAX_ATTEMPTS,
        DEFAULT_BASE_BACKOFF_MILLIS);
  }

  WebhookService(
      ApplicationRepository applicationRepository,
      ObjectMapper objectMapper,
      HttpSender httpSender,
      boolean notifyOnRegression,
      String platformBaseUrl,
      int maxAttempts,
      long baseBackoffMillis) {
    this.applicationRepository = Objects.requireNonNull(applicationRepository, "applicationRepository");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.httpSender = httpSender != null ? httpSender : new DefaultHttpSender();
    this.notifyOnRegression = notifyOnRegression;
    this.platformBaseUrl = normalize(platformBaseUrl);
    this.maxAttempts = maxAttempts;
    this.baseBackoffMillis = baseBackoffMillis;
  }

  public void notifyIncident(WebhookPayload payload) {
    if (payload == null) {
      throw new IllegalArgumentException("payload must not be null");
    }
    String appId = normalize(payload.appId());
    if (appId == null) {
      log.warn("Skipping webhook notification: missing appId");
      return;
    }
    Optional<ApplicationEntity> application = applicationRepository.findById(appId);
    if (application.isEmpty()) {
      log.debug("Skipping webhook notification: application not found for appId={}", appId);
      return;
    }
    String webhookUrl = normalize(application.get().getWebhookUrl());
    if (webhookUrl == null) {
      log.debug("Skipping webhook notification: webhookUrl not configured for appId={}", appId);
      return;
    }
    if (payload.isRegression() && !notifyOnRegression) {
      log.debug("Skipping regression webhook: disabled by configuration for appId={}", appId);
      return;
    }
    WebhookType webhookType = resolveType(webhookUrl);
    if (webhookType == WebhookType.UNKNOWN) {
      log.warn("Skipping webhook notification: unsupported webhookUrl={}", webhookUrl);
      return;
    }
    JsonNode body = buildRequestBody(payload, webhookType);
    sendWithRetry(webhookUrl, body);
  }

  public void notifyAnalysisComplete(IncidentEntity incident) {
    if (incident == null) {
      throw new IllegalArgumentException("incident must not be null");
    }
    if (incident.getStatus() != IncidentStatus.PENDING_REVIEW) {
      return;
    }
    String structuredOutput = normalize(incident.getAnalysisResult());
    if (structuredOutput == null) {
      return;
    }
    WebhookPayload.AnalysisInfo analysis =
        new WebhookPayload.AnalysisInfo(
            normalize(incident.getSessionId()),
            extractRootCause(structuredOutput),
            extractSummary(structuredOutput),
            extractSeverity(structuredOutput),
            buildDetailUrl(incident.getId()));
    WebhookPayload payload =
        new WebhookPayload(
            incident.getAppId(),
            incident.getId(),
            incident.getStatus(),
            incident.getErrorType(),
            incident.getErrorMessage(),
            incident.getRequestUrl(),
            incident.getRequestMethod(),
            incident.getRequestParams(),
            incident.getTraceId(),
            incident.getCreatedAt(),
            analysis);
    notifyIncident(payload);
  }

  JsonNode buildRequestBody(WebhookPayload payload, WebhookType type) {
    String title = "[HEALFLOW] 异常事件 " + safeStatus(payload.status());
    return switch (type) {
      case DINGTALK -> buildDingTalkPayload(title, formatDetails(payload, "- ", "", ""));
      case WECOM -> buildWeComPayload(title, formatDetails(payload, "- ", "", ""));
      case FEISHU -> buildFeishuPayload(title, formatDetails(payload, "", "**", "**"));
      case SLACK -> buildSlackPayload(title, formatDetails(payload, "", "*", "*"));
      case UNKNOWN -> objectMapper.createObjectNode();
    };
  }

  private ObjectNode buildDingTalkPayload(String title, String details) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("msgtype", "markdown");
    ObjectNode markdown = root.putObject("markdown");
    markdown.put("title", title);
    markdown.put("text", "### " + title + "\n" + details);
    return root;
  }

  private ObjectNode buildWeComPayload(String title, String details) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("msgtype", "markdown");
    ObjectNode markdown = root.putObject("markdown");
    markdown.put("content", "### " + title + "\n" + details);
    return root;
  }

  private ObjectNode buildFeishuPayload(String title, String details) {
    ObjectNode root = objectMapper.createObjectNode();
    root.put("msg_type", "interactive");
    ObjectNode card = root.putObject("card");
    ObjectNode header = card.putObject("header");
    ObjectNode headerTitle = header.putObject("title");
    headerTitle.put("tag", "plain_text");
    headerTitle.put("content", title);
    ArrayNode elements = card.putArray("elements");
    ObjectNode div = elements.addObject();
    ObjectNode text = div.putObject("text");
    text.put("tag", "lark_md");
    text.put("content", details);
    return root;
  }

  private ObjectNode buildSlackPayload(String title, String details) {
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode blocks = root.putArray("blocks");
    ObjectNode header = blocks.addObject();
    header.put("type", "header");
    ObjectNode headerText = header.putObject("text");
    headerText.put("type", "plain_text");
    headerText.put("text", title);
    ObjectNode section = blocks.addObject();
    section.put("type", "section");
    ObjectNode sectionText = section.putObject("text");
    sectionText.put("type", "mrkdwn");
    sectionText.put("text", details);
    return root;
  }

  private String formatDetails(WebhookPayload payload, String bulletPrefix, String labelStart, String labelEnd) {
    List<String> lines = new ArrayList<>();
    String status = safeStatus(payload.status());
    String priority = payload.isRegression() ? "高" : "普通";
    addLine(lines, bulletPrefix, labelStart, labelEnd, "状态", status);
    addLine(lines, bulletPrefix, labelStart, labelEnd, "优先级", priority);
    addLine(lines, bulletPrefix, labelStart, labelEnd, "应用", payload.appId());
    addLine(lines, bulletPrefix, labelStart, labelEnd, "事件ID", payload.incidentId());
    addLine(lines, bulletPrefix, labelStart, labelEnd, "异常类型", payload.errorType());
    addLine(lines, bulletPrefix, labelStart, labelEnd, "异常信息", payload.errorMessage());
    addLine(lines, bulletPrefix, labelStart, labelEnd, "请求接口", payload.requestUrl());
    addLine(lines, bulletPrefix, labelStart, labelEnd, "请求方法", payload.requestMethod());
    addLine(lines, bulletPrefix, labelStart, labelEnd, "请求参数", payload.requestParams());
    addLine(lines, bulletPrefix, labelStart, labelEnd, "链路ID", payload.traceId());
    Instant occurredAt = payload.occurredAt();
    if (occurredAt != null) {
      addLine(lines, bulletPrefix, labelStart, labelEnd, "发生时间", occurredAt.toString());
    }
    if (payload.hasAnalysis()) {
      WebhookPayload.AnalysisInfo analysis = payload.analysis();
      addLine(lines, bulletPrefix, labelStart, labelEnd, "会话ID", analysis.sessionId());
      addLine(lines, bulletPrefix, labelStart, labelEnd, "严重程度", formatSeverity(analysis.severity()));
      addLine(lines, bulletPrefix, labelStart, labelEnd, "根因", analysis.rootCause());
      addLine(lines, bulletPrefix, labelStart, labelEnd, "摘要", analysis.summary());
      addLine(lines, bulletPrefix, labelStart, labelEnd, "详情", analysis.detailUrl());
    }
    return String.join("\n", lines);
  }

  private String extractRootCause(String structuredOutput) {
    JsonNode node = parseStructuredOutput(structuredOutput);
    if (node == null) {
      return null;
    }
    String rootCause = extractText(node, "root_cause");
    return rootCause != null ? rootCause : extractText(node, "rootCause");
  }

  private String extractSeverity(String structuredOutput) {
    JsonNode node = parseStructuredOutput(structuredOutput);
    String severity = extractText(node, "severity");
    return severity != null ? severity.toUpperCase(Locale.ROOT) : null;
  }

  private String extractSummary(String structuredOutput) {
    JsonNode node = parseStructuredOutput(structuredOutput);
    String summary = extractText(node, "analysis");
    if (summary == null) {
      return null;
    }
    summary = summary.replace('\n', ' ').replace('\r', ' ');
    return truncate(summary, 300);
  }

  private String buildDetailUrl(String incidentId) {
    String baseUrl = normalize(platformBaseUrl);
    String normalizedIncidentId = normalize(incidentId);
    if (baseUrl == null || normalizedIncidentId == null) {
      return null;
    }
    if (baseUrl.endsWith("/")) {
      baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
    }
    return baseUrl + "/api/v1/incidents/" + normalizedIncidentId;
  }

  private JsonNode parseStructuredOutput(String structuredOutput) {
    String normalized = normalize(structuredOutput);
    if (normalized == null) {
      return null;
    }
    try {
      return objectMapper.readTree(normalized);
    } catch (IOException e) {
      log.debug("Failed to parse structuredOutput JSON", e);
      return null;
    }
  }

  private static String extractText(JsonNode node, String field) {
    if (node == null || field == null || field.isBlank()) {
      return null;
    }
    return normalize(node.path(field).asText(null));
  }

  private static String truncate(String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    if (maxLength <= 3) {
      return value.substring(0, maxLength);
    }
    return value.substring(0, maxLength - 3) + "...";
  }

  private static void addLine(
      List<String> lines,
      String bulletPrefix,
      String labelStart,
      String labelEnd,
      String label,
      String value) {
    if (value == null || value.isBlank()) {
      return;
    }
    lines.add(bulletPrefix + labelStart + label + labelEnd + ": " + value);
  }

  private void sendWithRetry(String webhookUrl, JsonNode body) {
    String payload;
    try {
      payload = objectMapper.writeValueAsString(body);
    } catch (IOException e) {
      log.warn("Failed to serialize webhook payload", e);
      return;
    }

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        HttpResponsePayload response = httpSender.postJson(webhookUrl, payload);
        if (response.isSuccess()) {
          return;
        }
        log.warn(
            "Webhook attempt {} failed with status {}",
            attempt,
            response.statusCode());
      } catch (Exception e) {
        log.warn("Webhook attempt {} failed", attempt, e);
      }

      if (attempt < maxAttempts && !backoff(attempt)) {
        return;
      }
    }
  }

  private boolean backoff(int attempt) {
    if (baseBackoffMillis <= 0) {
      return true;
    }
    long delay = baseBackoffMillis * (1L << (attempt - 1));
    try {
      Thread.sleep(delay);
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private static String safeStatus(IncidentStatus status) {
    if (status == null) {
      return "未知";
    }
    return switch (status) {
      case OPEN -> "待处理";
      case SKIP -> "跳过";
      case ANALYZING -> "分析中";
      case PENDING_REVIEW -> "待审核";
      case FIXED -> "已修复";
      case REGRESSION -> "回归";
      case IGNORED -> "已忽略";
    };
  }

  private static String formatSeverity(String severity) {
    String normalized = normalize(severity);
    if (normalized == null) {
      return null;
    }
    String upper = normalized.toUpperCase(Locale.ROOT);
    return switch (upper) {
      case "CRITICAL", "HIGH" -> "高";
      case "MEDIUM" -> "中";
      case "LOW" -> "低";
      default -> normalized;
    };
  }

  private static WebhookType resolveType(String webhookUrl) {
    String normalized = normalize(webhookUrl);
    if (normalized == null) {
      return WebhookType.UNKNOWN;
    }
    String lower = normalized.toLowerCase(Locale.ROOT);
    if (lower.contains("oapi.dingtalk.com")) {
      return WebhookType.DINGTALK;
    }
    if (lower.contains("qyapi.weixin.qq.com")) {
      return WebhookType.WECOM;
    }
    if (lower.contains("open.feishu.cn") || lower.contains("larksuite.com")) {
      return WebhookType.FEISHU;
    }
    if (lower.contains("hooks.slack.com")) {
      return WebhookType.SLACK;
    }
    return WebhookType.UNKNOWN;
  }

  enum WebhookType {
    DINGTALK,
    WECOM,
    FEISHU,
    SLACK,
    UNKNOWN
  }

  interface HttpSender {
    HttpResponsePayload postJson(String webhookUrl, String payload) throws Exception;
  }

  record HttpResponsePayload(int statusCode, String body) {
    boolean isSuccess() {
      return statusCode >= 200 && statusCode < 300;
    }
  }

  static final class DefaultHttpSender implements HttpSender {

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public HttpResponsePayload postJson(String webhookUrl, String payload)
        throws IOException, InterruptedException {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(webhookUrl))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(payload))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      return new HttpResponsePayload(response.statusCode(), response.body());
    }
  }
}

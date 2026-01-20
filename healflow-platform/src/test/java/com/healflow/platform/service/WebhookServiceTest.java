package com.healflow.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.common.dto.IncidentReport;
import com.healflow.common.enums.IncidentStatus;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.platform.dto.WebhookPayload;
import com.healflow.platform.entity.ApplicationEntity;
import com.healflow.platform.entity.ErrorFingerprintEntity;
import com.healflow.platform.entity.IncidentEntity;
import com.healflow.platform.repository.ApplicationRepository;
import com.healflow.platform.repository.IncidentRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebhookServiceTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void buildDingTalkPayloadUsesMarkdown() {
    WebhookService service = newWebhookService(mock(ApplicationRepository.class), new CapturingHttpSender(200));
    WebhookPayload payload = samplePayload(IncidentStatus.OPEN);

    JsonNode body = service.buildRequestBody(payload, WebhookService.WebhookType.DINGTALK);

    assertEquals("markdown", body.get("msgtype").asText());
    assertEquals("[HEALFLOW] 异常事件 待处理", body.get("markdown").get("title").asText());
    String text = body.get("markdown").get("text").asText();
    assertTrue(text.contains("优先级: 普通"));
    assertTrue(text.contains("请求接口: http://example.test/api/orders"));
    assertTrue(text.contains("链路ID: trace-xyz"));
  }

  @Test
  void buildFeishuPayloadUsesInteractiveCard() {
    WebhookService service = newWebhookService(mock(ApplicationRepository.class), new CapturingHttpSender(200));
    WebhookPayload payload = samplePayload(IncidentStatus.OPEN);

    JsonNode body = service.buildRequestBody(payload, WebhookService.WebhookType.FEISHU);

    assertEquals("interactive", body.get("msg_type").asText());
    JsonNode header = body.get("card").get("header").get("title");
    assertEquals("plain_text", header.get("tag").asText());
    assertEquals("[HEALFLOW] 异常事件 待处理", header.get("content").asText());
    assertEquals("lark_md", body.get("card").get("elements").get(0).get("text").get("tag").asText());
  }

  @Test
  void buildSlackPayloadUsesBlocks() {
    WebhookService service = newWebhookService(mock(ApplicationRepository.class), new CapturingHttpSender(200));
    WebhookPayload payload = samplePayload(IncidentStatus.OPEN);

    JsonNode body = service.buildRequestBody(payload, WebhookService.WebhookType.SLACK);

    assertEquals("header", body.get("blocks").get(0).get("type").asText());
    assertEquals("section", body.get("blocks").get(1).get("type").asText());
    assertEquals("mrkdwn", body.get("blocks").get(1).get("text").get("type").asText());
  }

  @Test
  void notifyIncidentSendsForNewIncident() throws Exception {
    ApplicationRepository repository = repositoryWithWebhook("app-1", "https://hooks.slack.com/services/abc");
    CapturingHttpSender sender = new CapturingHttpSender(200);
    WebhookService service = newWebhookService(repository, sender);

    service.notifyIncident(samplePayload(IncidentStatus.OPEN));

    assertEquals(1, sender.attempts());
    assertEquals("https://hooks.slack.com/services/abc", sender.lastUrl());
    JsonNode payload = OBJECT_MAPPER.readTree(sender.lastPayload());
    assertEquals("header", payload.get("blocks").get(0).get("type").asText());
  }

  @Test
  void notifyIncidentBuildsWeComMarkdownContentPayload() throws Exception {
    ApplicationRepository repository =
        repositoryWithWebhook("app-1", "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=abc");
    CapturingHttpSender sender = new CapturingHttpSender(200);
    WebhookService service = newWebhookService(repository, sender);

    service.notifyIncident(samplePayload(IncidentStatus.OPEN));

    assertEquals(1, sender.attempts());
    JsonNode payload = OBJECT_MAPPER.readTree(sender.lastPayload());
    assertEquals("markdown", payload.get("msgtype").asText());
    assertTrue(payload.hasNonNull("markdown"));
    JsonNode markdown = payload.get("markdown");
    assertTrue(markdown.hasNonNull("content"));
    String content = markdown.get("content").asText();
    assertTrue(content.contains("### [HEALFLOW] 异常事件 待处理"));
    assertTrue(content.contains("优先级: 普通"));
    assertFalse(markdown.has("text"));
  }

  @Test
  void notifyIncidentUsesHighPriorityForRegression() throws Exception {
    ApplicationRepository repository = repositoryWithWebhook("app-1", "https://hooks.slack.com/services/abc");
    CapturingHttpSender sender = new CapturingHttpSender(200);
    WebhookService service = newWebhookService(repository, sender, true, 1, 0);

    service.notifyIncident(samplePayload(IncidentStatus.REGRESSION));

    JsonNode payload = OBJECT_MAPPER.readTree(sender.lastPayload());
    String text = payload.get("blocks").get(1).get("text").get("text").asText();
    assertTrue(text.contains("*优先级*: 高"));
  }

  @Test
  void notifyIncidentSkipsRegressionWhenDisabled() {
    ApplicationRepository repository = repositoryWithWebhook("app-1", "https://hooks.slack.com/services/abc");
    CapturingHttpSender sender = new CapturingHttpSender(200);
    WebhookService service = newWebhookService(repository, sender, false, 1, 0);

    service.notifyIncident(samplePayload(IncidentStatus.REGRESSION));

    assertEquals(0, sender.attempts());
  }

  @Test
  void notifyIncidentRetriesOnFailure() {
    ApplicationRepository repository = repositoryWithWebhook("app-1", "https://hooks.slack.com/services/abc");
    CapturingHttpSender sender = new CapturingHttpSender(500);
    WebhookService service = newWebhookService(repository, sender, true, 3, 1);

    service.notifyIncident(samplePayload(IncidentStatus.OPEN));

    assertEquals(3, sender.attempts());
  }

  @Test
  void createIncidentTriggersWebhookNotification() {
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    when(incidentRepository.save(any(IncidentEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    GitWorkspaceManager gitManager = mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = mock(FingerprintService.class);
    when(fingerprintService.recordOccurrence(anyString(), anyString()))
        .thenReturn(new ErrorFingerprintEntity("fp-1", Instant.parse("2026-01-05T00:00:00Z")));
    ApplicationService applicationService = mock(ApplicationService.class);
    when(applicationService.getApplication("app-1"))
        .thenReturn(
            new ApplicationService.ApplicationResponse(
                "app-1", null, null, null, null, false, false, false, null));
    IncidentService service =
        new IncidentService(
            gitManager, dockerSandboxManager, incidentRepository, fingerprintService, applicationService, "sandbox", "ai", "");
    WebhookService webhookService = mock(WebhookService.class);
    service.setWebhookService(webhookService);

    IncidentReport report =
        new IncidentReport(
            "app-1",
            "https://example.invalid/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "stack",
            null,
            null,
            null,
            null,
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    service.createIncident(report);

    verify(webhookService)
        .notifyIncident(
            argThat(
                payload ->
                    payload.appId().equals("app-1")
                        && payload.status() == IncidentStatus.OPEN
                        && payload.errorType().equals("NullPointerException")));
  }

  @Test
  void regressionStatusTriggersWebhook() throws Exception {
    IncidentRepository incidentRepository = mock(IncidentRepository.class);
    IncidentEntity fixed = new IncidentEntity("inc-fixed", "app-1", IncidentStatus.FIXED);
    when(incidentRepository.findById("inc-fixed")).thenReturn(Optional.of(fixed));
    GitWorkspaceManager gitManager = mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, mock(ApplicationService.class), "sandbox", "ai", "");
    WebhookService webhookService = mock(WebhookService.class);
    service.setWebhookService(webhookService);

    IncidentReport report =
        new IncidentReport(
            "app-1",
            "https://example.invalid/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "stack",
            null,
            null,
            null,
            null,
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    IncidentEntity updated = invokeFindOrCreateIncident(service, "inc-fixed", report);

    assertEquals(IncidentStatus.REGRESSION, updated.getStatus());
    verify(webhookService, never()).notifyIncident(any());
  }

  @Test
  void notifyAnalysisCompleteIncludesRootCauseAndDetailUrl() throws Exception {
    ApplicationRepository repository = repositoryWithWebhook("app-1", "https://hooks.slack.com/services/abc");
    CapturingHttpSender sender = new CapturingHttpSender(200);
    WebhookService service =
        new WebhookService(repository, OBJECT_MAPPER, sender, true, "https://platform.example", 1, 0);

    IncidentEntity incident = new IncidentEntity("inc-1", "app-1", IncidentStatus.PENDING_REVIEW);
    incident.setSessionId("sess-123");
    incident.setErrorType("NullPointerException");
    incident.setErrorMessage("boom");
    incident.setAnalysisResult(
        "{\"severity\":\"high\",\"root_cause\":\"Uninitialized variable\",\"analysis\":\"The error occurs because...\"}");

    service.notifyAnalysisComplete(incident);

    JsonNode payload = OBJECT_MAPPER.readTree(sender.lastPayload());
    String text = payload.get("blocks").get(1).get("text").get("text").asText();
    assertTrue(text.contains("*严重程度*: 高"));
    assertTrue(text.contains("*根因*: Uninitialized variable"));
    assertTrue(text.contains("*详情*: https://platform.example/api/v1/incidents/inc-1"));
  }

  private static WebhookPayload samplePayload(IncidentStatus status) {
    return new WebhookPayload(
        "app-1",
        "inc-1",
        status,
        "NullPointerException",
        "boom",
        "http://example.test/api/orders",
        "GET",
        "id=1",
        "trace-xyz",
        Instant.parse("2026-01-05T00:00:00Z"),
        null);
  }

  private static WebhookService newWebhookService(
      ApplicationRepository repository, CapturingHttpSender sender) {
    return newWebhookService(repository, sender, true, 1, 0);
  }

  private static WebhookService newWebhookService(
      ApplicationRepository repository,
      CapturingHttpSender sender,
      boolean notifyOnRegression,
      int maxAttempts,
      long baseBackoffMillis) {
    return new WebhookService(
        repository, OBJECT_MAPPER, sender, notifyOnRegression, "", maxAttempts, baseBackoffMillis);
  }

  private static ApplicationRepository repositoryWithWebhook(String appId, String webhookUrl) {
    ApplicationRepository repository = mock(ApplicationRepository.class);
    ApplicationEntity application = new ApplicationEntity(appId);
    application.setWebhookUrl(webhookUrl);
    when(repository.findById(appId)).thenReturn(Optional.of(application));
    return repository;
  }

  private static IncidentEntity invokeFindOrCreateIncident(
      IncidentService service, String incidentId, IncidentReport report) throws Exception {
    var method =
        IncidentService.class.getDeclaredMethod("findOrCreateIncident", String.class, IncidentReport.class);
    method.setAccessible(true);
    return (IncidentEntity) method.invoke(service, incidentId, report);
  }

  private static final class CapturingHttpSender implements WebhookService.HttpSender {

    private final int statusCode;
    private int attempts;
    private String lastPayload;
    private String lastUrl;

    private CapturingHttpSender(int statusCode) {
      this.statusCode = statusCode;
    }

    @Override
    public WebhookService.HttpResponsePayload postJson(String webhookUrl, String payload) {
      attempts++;
      lastUrl = webhookUrl;
      lastPayload = payload;
      return new WebhookService.HttpResponsePayload(statusCode, "body");
    }

    int attempts() {
      return attempts;
    }

    String lastPayload() {
      return lastPayload;
    }

    String lastUrl() {
      return lastUrl;
    }
  }
}

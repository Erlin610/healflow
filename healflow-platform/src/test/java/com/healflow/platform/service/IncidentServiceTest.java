package com.healflow.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healflow.common.dto.AnalysisResult;
import com.healflow.common.dto.FixProposal;
import com.healflow.common.dto.FixResult;
import com.healflow.common.dto.IncidentReport;
import com.healflow.engine.HealingResult;
import com.healflow.engine.HealflowEngine;
import com.healflow.engine.Severity;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellRunner;
import com.healflow.engine.shell.ShellTimeoutException;
import com.healflow.platform.controller.IncidentController;
import com.healflow.platform.controller.ReportController;
import com.healflow.platform.controller.ReportRequest;
import com.healflow.common.enums.IncidentStatus;
import com.healflow.platform.entity.IncidentEntity;
import com.healflow.platform.repository.IncidentRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
      "logging.level.com.healflow.platform.service.IncidentService=OFF",
      "healflow.sandbox.image=test-image",
      "healflow.encryption.key=test-key"
    })
class IncidentServiceTest {

  @Autowired private IncidentService incidentService;
  @Autowired private IncidentRepository incidentRepository;
  @MockBean private GitWorkspaceManager gitManager;
  @MockBean private ShellRunner shellRunner;

  // Tests for old async processIncident() removed - replaced with database persistence in Phase 5

  @Test
  void reportRequestStoresIncidentReport() {
    ReportRequest request = new ReportRequest("boom");
    assertEquals("boom", request.incidentReport());
  }

  @Test
  void incidentControllerDelegatesAndReturnsIncidentId() {
    IncidentService delegate = Mockito.mock(IncidentService.class);
    IncidentController controller = new IncidentController(delegate);
    IncidentReport report =
        new IncidentReport(
            "app-789",
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

    when(delegate.createIncident(report)).thenReturn("inc-123");
    var response = controller.receiveReport(report);
    assertEquals(200, response.getStatusCode().value());
    assertEquals("inc-123", response.getBody().get("incidentId"));
    assertEquals("OPEN", response.getBody().get("status"));
    verify(delegate).createIncident(report);
  }

  @Test
  void analyzeIncidentWithMockedDockerReturnsValidJson(@TempDir Path tempDir) throws Exception {
    incidentRepository.deleteAll();

    Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            incidentRepository,
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox",
            "ai-image",
            "");

    try {
      System.setProperty("user.home", home.toString());
      Files.createDirectories(home.resolve(".claude"));
      Files.writeString(
          home.resolve(".claude/settings.json"),
          "{\"env\":{\"ANTHROPIC_API_KEY\":\"test-key\"}}");

      IncidentReport report =
          new IncidentReport(
              "test-app",
              "https://example.invalid/repo.git",
              "main",
              "NullPointerException",
              "Object reference not set",
              "at com.example.Main.main(Main.java:10)",
              null,
              null,
              null,
              null,
              Map.of(),
              Instant.parse("2026-01-05T00:00:00Z"));

      when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
          .thenReturn(workspace);

      String validClaudeJson =
          "{\"bug_type\":\"NullPointerException\",\"severity\":\"high\","
              + "\"root_cause\":\"Uninitialized variable\",\"affected_files\":[\"Main.java\"],"
              + "\"analysis\":\"The error occurs because...\",\"confidence\":0.9}";
      String sandboxOutput =
          "{\"session_id\":\"sess-test-123\",\"structured_output\":"
              + validClaudeJson
              + ",\"result\":\"Analysis complete\"}";

      when(dockerSandboxManager.executeInteractiveRunInSandbox(
              any(String.class),
              any(Path.class),
              any(String.class),
              any(String.class),
              any(Map.class),
              any(List.class),
              any(java.time.Duration.class),
              any(List.class)))
          .thenReturn(new com.healflow.engine.shell.CommandResult(0, sandboxOutput));

      AnalysisResult result = service.analyzeIncident("inc-mock-test", report);

      assertEquals("sess-test-123", result.sessionId());
      assertEquals(validClaudeJson, result.structuredOutput());
      assertEquals("Analysis complete", result.fullText());

      IncidentEntity persisted = incidentRepository.findById("inc-mock-test").orElseThrow();
      assertEquals(IncidentStatus.PENDING_REVIEW, persisted.getStatus());
      assertEquals("sess-test-123", persisted.getSessionId());
      assertEquals(validClaudeJson, persisted.getAnalysisResult());

      var argvCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(dockerSandboxManager)
          .executeInteractiveRunInSandbox(
              any(String.class),
              any(Path.class),
              any(String.class),
              any(String.class),
              any(Map.class),
              argvCaptor.capture(),
              any(java.time.Duration.class),
              any(List.class));

      @SuppressWarnings("unchecked")
      List<String> argv = argvCaptor.getValue();
      assertEquals("sh", argv.get(0));
      assertTrue(argv.get(1).startsWith("/src/analyze-incident-"));
      assertTrue(argv.get(1).endsWith(".sh"));
    } finally {
      if (previousUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", previousUserHome);
      }
    }
  }

  @Test
  void incidentControllerAnalyzesGeneratesAndAppliesViaService() {
    IncidentService delegate = Mockito.mock(IncidentService.class);
    IncidentController controller = new IncidentController(delegate);
    IncidentReport report =
        new IncidentReport(
            "app-789",
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

    when(delegate.analyzeIncident("inc-1", report))
        .thenReturn(new AnalysisResult("sess-1", "{\"ok\":true}", "analysis"));
    assertEquals(200, controller.analyze("inc-1", report).getStatusCode().value());

    when(delegate.generateFix("inc-1")).thenReturn(new FixProposal("sess-1", "{\"fix\":true}", "proposal"));
    assertEquals(200, controller.generateFix("inc-1").getStatusCode().value());

    when(delegate.applyFix("inc-1")).thenReturn(new FixResult("APPLIED", "{\"usage\":1}"));
    assertEquals(200, controller.applyFix("inc-1").getStatusCode().value());

    verify(delegate).analyzeIncident("inc-1", report);
    verify(delegate).generateFix("inc-1");
    verify(delegate).applyFix("inc-1");
  }

  @Test
  void incidentControllerReturnsBadRequestForInvalidState() {
    IncidentService delegate = Mockito.mock(IncidentService.class);
    IncidentController controller = new IncidentController(delegate);
    IncidentReport report =
        new IncidentReport(
            "app-789",
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

    when(delegate.analyzeIncident("inc-1", report)).thenThrow(new IllegalStateException("bad"));
    assertEquals(400, controller.analyze("inc-1", report).getStatusCode().value());

    when(delegate.generateFix("inc-1")).thenThrow(new IllegalArgumentException("bad"));
    assertEquals(400, controller.generateFix("inc-1").getStatusCode().value());

    when(delegate.applyFix("inc-1")).thenThrow(new IllegalStateException("bad"));
    assertEquals(400, controller.applyFix("inc-1").getStatusCode().value());
  }

  @Test
  void incidentControllerReturnsInternalServerErrorForUnexpectedFailures() {
    IncidentService delegate = Mockito.mock(IncidentService.class);
    IncidentController controller = new IncidentController(delegate);
    IncidentReport report =
        new IncidentReport(
            "app-789",
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

    when(delegate.analyzeIncident("inc-1", report)).thenThrow(new RuntimeException("boom"));
    assertEquals(500, controller.analyze("inc-1", report).getStatusCode().value());

    when(delegate.generateFix("inc-1")).thenThrow(new RuntimeException("boom"));
    assertEquals(500, controller.generateFix("inc-1").getStatusCode().value());

    when(delegate.applyFix("inc-1")).thenThrow(new RuntimeException("boom"));
    assertEquals(500, controller.applyFix("inc-1").getStatusCode().value());
  }

  @Test
  void reportControllerStatusAndReportDelegate() {
    HealingService healingService = Mockito.mock(HealingService.class);
    ReportController controller = new ReportController(healingService);
    HealingResult expected = new HealingResult(Severity.LOW, "ok");

    assertEquals(Map.of("status", "UP"), controller.status());
    when(healingService.analyze("boom")).thenReturn(expected);
    assertEquals(expected, controller.report(new ReportRequest("boom")));
  }

  @Test
  void healingServiceDelegatesToEngine() {
    HealflowEngine engine = Mockito.mock(HealflowEngine.class);
    HealingService service = new HealingService(engine);
    HealingResult expected = new HealingResult(Severity.HIGH, "bad");

    when(engine.analyze("boom")).thenReturn(expected);
    assertEquals(expected, service.analyze("boom"));
  }

  @Test
  void persistedAnalyzeTransitionsOpenToPendingReviewAndStoresSession() {
    incidentRepository.deleteAll();

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        Mockito.spy(
            new IncidentService(
                gitManager,
                dockerSandboxManager,
                incidentRepository,
                fingerprintService,
                Mockito.mock(ApplicationService.class),
                "sandbox",
                "ai",
                ""));

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

    Mockito.doAnswer(invocation -> {
          IncidentEntity inProgress = incidentRepository.findById("inc-1").orElseThrow();
          assertEquals(IncidentStatus.ANALYZING, inProgress.getStatus());
          return new AnalysisResult("sess-1", "{\"ok\":true}", "analysis");
        })
        .when(service)
        .analyzeIncident(Mockito.eq(report), any(String.class));

    AnalysisResult result = service.analyzeIncident("inc-1", report);
    assertEquals("sess-1", result.sessionId());

    IncidentEntity persisted = incidentRepository.findById("inc-1").orElseThrow();
    assertEquals(IncidentStatus.PENDING_REVIEW, persisted.getStatus());
    assertEquals("app-1", persisted.getAppId());
    assertEquals("sess-1", persisted.getSessionId());
    assertEquals("{\"ok\":true}", persisted.getAnalysisResult());
    assertNotNull(persisted.getCreatedAt());
    assertNotNull(persisted.getUpdatedAt());
  }

  @Test
  void persistedAnalyzeUpdatesExistingIncidentMetadata() {
    incidentRepository.deleteAll();

    IncidentEntity existing = new IncidentEntity("inc-existing", "old-app", IncidentStatus.OPEN);
    existing.setRepoUrl("https://example.invalid/old.git");
    existing.setBranch("old");
    incidentRepository.saveAndFlush(existing);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        Mockito.spy(
            new IncidentService(
                gitManager,
                dockerSandboxManager,
                incidentRepository,
                fingerprintService,
                Mockito.mock(ApplicationService.class),
                "sandbox",
                "ai",
                ""));

    IncidentReport report =
        new IncidentReport(
            "new-app",
            "https://example.invalid/new.git",
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

    Mockito.doReturn(new AnalysisResult("sess-1", "{\"ok\":true}", "analysis"))
        .when(service)
        .analyzeIncident(Mockito.eq(report), any(String.class));

    service.analyzeIncident("inc-existing", report);

    IncidentEntity persisted = incidentRepository.findById("inc-existing").orElseThrow();
    assertEquals("new-app", persisted.getAppId());
    assertEquals("https://example.invalid/new.git", persisted.getRepoUrl());
    assertEquals("main", persisted.getBranch());
    assertEquals(IncidentStatus.PENDING_REVIEW, persisted.getStatus());
  }

  @Test
  void findOrCreateIncidentMarksFixedAsRegression() throws Exception {
    incidentRepository.deleteAll();

    IncidentEntity fixed = new IncidentEntity("inc-fixed", "app-1", IncidentStatus.FIXED);
    incidentRepository.saveAndFlush(fixed);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

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
  }

  @Test
  void persistedAnalyzeRejectsInvalidTransition() {
    incidentRepository.deleteAll();
    incidentRepository.saveAndFlush(new IncidentEntity("inc-bad", "app-1", IncidentStatus.PENDING_REVIEW));

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

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

    assertThrows(IllegalStateException.class, () -> service.analyzeIncident("inc-bad", report));
  }

  @Test
  void persistedOperationsRejectMissingIncidentId() {
    incidentRepository.deleteAll();

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

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

    assertThrows(IllegalArgumentException.class, () -> service.analyzeIncident(" ", report));
    assertThrows(IllegalArgumentException.class, () -> service.generateFix(" "));
    assertThrows(IllegalArgumentException.class, () -> service.applyFix(" "));
  }

  @Test
  void persistedGenerateFixRejectsMissingIncident() {
    incidentRepository.deleteAll();

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

    assertThrows(IllegalArgumentException.class, () -> service.generateFix("missing"));
  }

  @Test
  void persistedAnalyzeMarksIncidentFailedWhenDelegateThrows() {
    incidentRepository.deleteAll();

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        Mockito.spy(new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", ""));

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

    Mockito.doThrow(new RuntimeException("boom")).when(service).analyzeIncident(Mockito.eq(report), any(String.class));
    assertThrows(RuntimeException.class, () -> service.analyzeIncident("inc-fail", report));

    IncidentEntity persisted = incidentRepository.findById("inc-fail").orElseThrow();
    assertEquals(IncidentStatus.OPEN, persisted.getStatus());
  }

  @Test
  void persistedGenerateFixAndApplyFixUpdatesProposalAndFixesIncident() {
    incidentRepository.deleteAll();

    IncidentEntity incident = new IncidentEntity("inc-2", "app-2", IncidentStatus.PENDING_REVIEW);
    incident.setRepoUrl("https://example.invalid/repo.git");
    incident.setBranch("main");
    incident.setSessionId("sess-2");
    incidentRepository.saveAndFlush(incident);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        Mockito.spy(new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", ""));

    Path workspace = Path.of("build-scratch/workspace");
    Mockito.when(gitManager.prepareWorkspace("app-2", "https://example.invalid/repo.git", "main"))
        .thenReturn(workspace);

    Mockito.doReturn(new FixProposal("sess-2", "{\"fix\":true}", "proposal"))
        .when(service)
        .generateFix("sess-2", workspace);

    FixProposal proposal = service.generateFix("inc-2");
    assertEquals("sess-2", proposal.sessionId());

    IncidentEntity afterFixProposal = incidentRepository.findById("inc-2").orElseThrow();
    assertEquals(IncidentStatus.PENDING_REVIEW, afterFixProposal.getStatus());
    assertEquals("{\"fix\":true}", afterFixProposal.getFixProposal());

    Mockito.doReturn(new FixResult("APPLIED", "{\"usage\":1}"))
        .when(service)
        .applyFix("sess-2", workspace);

    FixResult fixResult = service.applyFix("inc-2");
    assertEquals("APPLIED", fixResult.result());

    IncidentEntity afterApply = incidentRepository.findById("inc-2").orElseThrow();
    assertEquals(IncidentStatus.FIXED, afterApply.getStatus());
  }

  @Test
  void persistedGenerateFixKeepsPendingReviewWhenDelegateThrows() {
    incidentRepository.deleteAll();

    IncidentEntity incident = new IncidentEntity("inc-fix-fail", "app-2", IncidentStatus.PENDING_REVIEW);
    incident.setRepoUrl("https://example.invalid/repo.git");
    incident.setBranch("main");
    incident.setSessionId("sess-2");
    incidentRepository.saveAndFlush(incident);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        Mockito.spy(new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", ""));

    Path workspace = Path.of("build-scratch/workspace");
    Mockito.when(gitManager.prepareWorkspace("app-2", "https://example.invalid/repo.git", "main"))
        .thenReturn(workspace);

    Mockito.doThrow(new RuntimeException("boom")).when(service).generateFix("sess-2", workspace);
    assertThrows(RuntimeException.class, () -> service.generateFix("inc-fix-fail"));

    IncidentEntity persisted = incidentRepository.findById("inc-fix-fail").orElseThrow();
    assertEquals(IncidentStatus.PENDING_REVIEW, persisted.getStatus());
  }

  @Test
  void persistedApplyFixKeepsPendingReviewWhenDelegateThrows() {
    incidentRepository.deleteAll();

    IncidentEntity incident = new IncidentEntity("inc-apply-fail", "app-2", IncidentStatus.PENDING_REVIEW);
    incident.setRepoUrl("https://example.invalid/repo.git");
    incident.setBranch("main");
    incident.setSessionId("sess-2");
    incidentRepository.saveAndFlush(incident);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        Mockito.spy(new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", ""));

    Path workspace = Path.of("build-scratch/workspace");
    Mockito.when(gitManager.prepareWorkspace("app-2", "https://example.invalid/repo.git", "main"))
        .thenReturn(workspace);

    Mockito.doThrow(new RuntimeException("boom")).when(service).applyFix("sess-2", workspace);
    assertThrows(RuntimeException.class, () -> service.applyFix("inc-apply-fail"));

    IncidentEntity persisted = incidentRepository.findById("inc-apply-fail").orElseThrow();
    assertEquals(IncidentStatus.PENDING_REVIEW, persisted.getStatus());
  }

  @Test
  void persistedGenerateFixRejectsInvalidState() {
    incidentRepository.deleteAll();
    incidentRepository.saveAndFlush(new IncidentEntity("inc-3", "app-3", IncidentStatus.OPEN));

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

    assertThrows(IllegalStateException.class, () -> service.generateFix("inc-3"));
    assertEquals(IncidentStatus.OPEN, incidentRepository.findById("inc-3").orElseThrow().getStatus());
  }

  @Test
  void persistedGenerateFixRejectsMissingWorkspaceMetadata() {
    incidentRepository.deleteAll();

    IncidentEntity incident = new IncidentEntity("inc-missing", "app-2", IncidentStatus.PENDING_REVIEW);
    incident.setSessionId("sess-2");
    incidentRepository.saveAndFlush(incident);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

    assertThrows(IllegalStateException.class, () -> service.generateFix("inc-missing"));
  }

  @Test
  void persistedGenerateFixRejectsMissingSessionId() {
    incidentRepository.deleteAll();

    IncidentEntity incident = new IncidentEntity("inc-no-session", "app-2", IncidentStatus.PENDING_REVIEW);
    incident.setRepoUrl("https://example.invalid/repo.git");
    incident.setBranch("main");
    incidentRepository.saveAndFlush(incident);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

    assertThrows(IllegalStateException.class, () -> service.generateFix("inc-no-session"));
  }

  @Test
  void persistedApplyFixRejectsInvalidState() {
    incidentRepository.deleteAll();

    IncidentEntity incident = new IncidentEntity("inc-not-fixing", "app-2", IncidentStatus.OPEN);
    incident.setSessionId("sess-2");
    incident.setRepoUrl("https://example.invalid/repo.git");
    incident.setBranch("main");
    incidentRepository.saveAndFlush(incident);

    GitWorkspaceManager gitManager = Mockito.mock(GitWorkspaceManager.class);
    DockerSandboxManager dockerSandboxManager = Mockito.mock(DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(gitManager, dockerSandboxManager, incidentRepository, fingerprintService, Mockito.mock(ApplicationService.class), "sandbox", "ai", "");

    assertThrows(IllegalStateException.class, () -> service.applyFix("inc-not-fixing"));
  }

  @Test
  void incidentStatusAllowsOnlyExpectedTransitions() {
    assertTrue(IncidentStatus.OPEN.canTransitionTo(IncidentStatus.ANALYZING));
    assertTrue(IncidentStatus.ANALYZING.canTransitionTo(IncidentStatus.PENDING_REVIEW));
    assertTrue(IncidentStatus.PENDING_REVIEW.canTransitionTo(IncidentStatus.FIXED));
    assertTrue(IncidentStatus.FIXED.canTransitionTo(IncidentStatus.REGRESSION));
    assertTrue(IncidentStatus.REGRESSION.canTransitionTo(IncidentStatus.ANALYZING));
    assertTrue(IncidentStatus.OPEN.canTransitionTo(IncidentStatus.IGNORED));

    assertTrue(!IncidentStatus.OPEN.canTransitionTo(IncidentStatus.FIXED));
    assertTrue(!IncidentStatus.IGNORED.canTransitionTo(IncidentStatus.OPEN));
    assertTrue(!IncidentStatus.OPEN.canTransitionTo(IncidentStatus.OPEN));
    assertTrue(!IncidentStatus.OPEN.canTransitionTo(null));
  }

  @Test
  void sanitizeContainerNameHandlesNullBlankAndInvalidCharacters() throws Exception {
    assertEquals("app", invokeSanitizeContainerNameComponent(null));
    assertEquals("app", invokeSanitizeContainerNameComponent("   "));
    assertEquals("ok-._123", invokeSanitizeContainerNameComponent("ok-._123"));
    assertEquals("a-b-c", invokeSanitizeContainerNameComponent("a b/c"));
    assertEquals("healflow-sandbox-app-123", invokeBuildContainerName("app-123"));
  }

  @Test
  void getApiKeyFromHostReturnsNullWhenSettingsMissingOrInvalid(@TempDir Path tempDir) throws Exception {
    String envApiKey = System.getenv("ANTHROPIC_API_KEY");
    String envAuthToken = System.getenv("ANTHROPIC_AUTH_TOKEN");
    org.junit.jupiter.api.Assumptions.assumeTrue(
        (envApiKey == null || envApiKey.isBlank()) && (envAuthToken == null || envAuthToken.isBlank()));

    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            Mockito.mock(com.healflow.platform.repository.IncidentRepository.class),
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox-image",
            "ai-image",
            "");

    try {
      System.setProperty("user.home", home.toString());
      assertEquals(null, invokeGetApiKeyFromHost(service));

      writeClaudeSettings(home, "not-json");
      assertEquals(null, invokeGetApiKeyFromHost(service));
    } finally {
      if (previousUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", previousUserHome);
      }
    }
  }

  @Test
  void analyzeIncidentParsesSandboxJsonAndPassesPrompt(@TempDir Path tempDir) throws Exception {
    Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            Mockito.mock(com.healflow.platform.repository.IncidentRepository.class),
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox-image",
            "ai-image",
            "");

    try {
      System.setProperty("user.home", home.toString());
      writeClaudeSettings(home, "{\"env\":{\"ANTHROPIC_API_KEY\":\"settings-key\"}}");

      String expectedApiKey = System.getenv("ANTHROPIC_API_KEY");
      if (expectedApiKey == null || expectedApiKey.isBlank()) {
        expectedApiKey = "settings-key";
      }

      String stackTrace = "x".repeat(1200);
      IncidentReport report =
          new IncidentReport(
              "app-123",
              "https://example.invalid/repo.git",
              "main",
              "NullPointerException",
              "boom",
              stackTrace,
              null,
              null,
              null,
              null,
              Map.of(),
              Instant.parse("2026-01-05T00:00:00Z"));

      when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
          .thenReturn(workspace);

      String structuredOutput = "{\"bug_type\":\"npe\"}";
      String sandboxJson =
          "{\"session_id\":\"sess-123\",\"structured_output\":"
              + structuredOutput
              + ",\"result\":\"analysis\"}";
      when(dockerSandboxManager.executeInteractiveRunInSandbox(
              any(String.class),
              any(Path.class),
              any(String.class),
              any(String.class),
              any(Map.class),
              any(List.class),
              any(java.time.Duration.class),
              any(List.class)))
          .thenReturn(new CommandResult(0, sandboxJson));

      String containerName = "healflow-sandbox-app-123";
      com.healflow.common.dto.AnalysisResult result = service.analyzeIncident(report, containerName);
      assertEquals("sess-123", result.sessionId());
      assertEquals(structuredOutput, result.structuredOutput());
      assertEquals("analysis", result.fullText());

      var environmentCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
      var argvCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(dockerSandboxManager)
          .executeInteractiveRunInSandbox(
              org.mockito.ArgumentMatchers.eq(containerName),
              org.mockito.ArgumentMatchers.eq(workspace),
              org.mockito.ArgumentMatchers.eq("/src"),
              org.mockito.ArgumentMatchers.eq("ai-image"),
              environmentCaptor.capture(),
              argvCaptor.capture(),
              any(java.time.Duration.class),
              any(List.class));

      @SuppressWarnings("unchecked")
      Map<String, String> environment = environmentCaptor.getValue();
      assertTrue(environment.isEmpty());

      @SuppressWarnings("unchecked")
      List<String> argv = argvCaptor.getValue();
      assertEquals("sh", argv.get(0));
      assertTrue(argv.get(1).startsWith("/src/analyze-incident-"));
      assertTrue(argv.get(1).endsWith(".sh"));
    } finally {
      if (previousUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", previousUserHome);
      }
    }
  }

  @Test
  void generateFixParsesSandboxJson(@TempDir Path tempDir) throws Exception {
    Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            Mockito.mock(com.healflow.platform.repository.IncidentRepository.class),
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox-image",
            "ai-image",
            "");

    try {
      System.setProperty("user.home", home.toString());
      writeClaudeSettings(home, "{\"apiKey\":\"settings-key\"}");

      String expectedApiKey = System.getenv("ANTHROPIC_API_KEY");
      if (expectedApiKey == null || expectedApiKey.isBlank()) {
        expectedApiKey = "settings-key";
      }

      String sessionId = "sess-456";
      String structuredOutput = "{\"fix_description\":\"ok\",\"files_to_modify\":[\"A.java\"],\"code_changes\":\"...\"}";
      String sandboxJson =
          "{\"structured_output\":" + structuredOutput + ",\"result\":\"proposal\"}";
      when(dockerSandboxManager.executeInteractiveRunInSandbox(
              any(String.class),
              any(Path.class),
              any(String.class),
              any(String.class),
              any(Map.class),
              any(List.class),
              any(java.time.Duration.class),
              any(List.class)))
          .thenReturn(new CommandResult(0, sandboxJson));

      com.healflow.common.dto.FixProposal proposal = service.generateFix(sessionId, workspace);
      assertEquals(sessionId, proposal.sessionId());
      assertEquals(structuredOutput, proposal.structuredOutput());
      assertEquals("proposal", proposal.fullText());

      var environmentCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
      var argvCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(dockerSandboxManager)
          .executeInteractiveRunInSandbox(
              org.mockito.ArgumentMatchers.eq("healflow-sandbox-fix-gen"),
              org.mockito.ArgumentMatchers.eq(workspace),
              org.mockito.ArgumentMatchers.eq("/src"),
              org.mockito.ArgumentMatchers.eq("ai-image"),
              environmentCaptor.capture(),
              argvCaptor.capture(),
              any(java.time.Duration.class),
              any(List.class));

      @SuppressWarnings("unchecked")
      Map<String, String> environment = environmentCaptor.getValue();
      assertEquals(expectedApiKey, environment.get("ANTHROPIC_API_KEY"));

      @SuppressWarnings("unchecked")
      List<String> argv = argvCaptor.getValue();
      assertEquals("sh", argv.get(0));
      assertEquals("-c", argv.get(1));
      String command = argv.get(2);
      assertTrue(command.contains("--resume " + sessionId));
      assertTrue(command.contains("--allowedTools Read"));
    } finally {
      if (previousUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", previousUserHome);
      }
    }
  }

  @Test
  void applyFixParsesSandboxJson(@TempDir Path tempDir) throws Exception {
    Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            Mockito.mock(com.healflow.platform.repository.IncidentRepository.class),
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox-image",
            "ai-image",
            "");

    try {
      System.setProperty("user.home", home.toString());
      writeClaudeSettings(home, "{\"env\":{\"anthropicApiKey\":\"settings-key\"}}");

      String expectedApiKey = System.getenv("ANTHROPIC_API_KEY");
      if (expectedApiKey == null || expectedApiKey.isBlank()) {
        expectedApiKey = "settings-key";
      }

      String sessionId = "sess-789";
      String sandboxJson =
          "{\"result\":\"done\",\"usage\":{\"input_tokens\":1,\"output_tokens\":2}}";
      when(dockerSandboxManager.executeInteractiveRunInSandbox(
              any(String.class),
              any(Path.class),
              any(String.class),
              any(String.class),
              any(Map.class),
              any(List.class),
              any(java.time.Duration.class),
              any(List.class)))
          .thenReturn(new CommandResult(0, sandboxJson));

      com.healflow.common.dto.FixResult fixResult = service.applyFix(sessionId, workspace);
      assertEquals("done", fixResult.result());
      assertEquals("{\"input_tokens\":1,\"output_tokens\":2}", fixResult.usage());

      var environmentCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
      var argvCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(dockerSandboxManager)
          .executeInteractiveRunInSandbox(
              org.mockito.ArgumentMatchers.eq("healflow-sandbox-fix-apply"),
              org.mockito.ArgumentMatchers.eq(workspace),
              org.mockito.ArgumentMatchers.eq("/src"),
              org.mockito.ArgumentMatchers.eq("ai-image"),
              environmentCaptor.capture(),
              argvCaptor.capture(),
              any(java.time.Duration.class),
              any(List.class));

      @SuppressWarnings("unchecked")
      Map<String, String> environment = environmentCaptor.getValue();
      assertEquals(expectedApiKey, environment.get("ANTHROPIC_API_KEY"));

      @SuppressWarnings("unchecked")
      List<String> argv = argvCaptor.getValue();
      assertEquals("claude", argv.get(0));
      assertTrue(argv.contains("--resume"));
      assertTrue(argv.contains(sessionId));
      assertTrue(argv.contains("--allowedTools"));
      assertTrue(argv.contains("Read,Edit,Write,Bash(mvn test:*)"));
    } finally {
      if (previousUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", previousUserHome);
      }
    }
  }

  @Test
  void getApiKeyFromHostPrefersEnvVarOtherwiseReadsSettings(@TempDir Path tempDir) throws Exception {
    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            Mockito.mock(com.healflow.platform.repository.IncidentRepository.class),
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox-image",
            "ai-image",
            "");

    try {
      System.setProperty("user.home", home.toString());
      writeClaudeSettings(home, "{\"apiKey\":\"settings-key\"}");

      String envApiKey = System.getenv("ANTHROPIC_API_KEY");
      String expected = (envApiKey == null || envApiKey.isBlank()) ? "settings-key" : envApiKey;
      assertEquals(expected, invokeGetApiKeyFromHost(service));
    } finally {
      if (previousUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", previousUserHome);
      }
    }
  }

  @Test
  void getApiKeyFromHostReadsSettingsJsonMultipleShapes(@TempDir Path tempDir) throws Exception {
    String envApiKey = System.getenv("ANTHROPIC_API_KEY");
    org.junit.jupiter.api.Assumptions.assumeTrue(envApiKey == null || envApiKey.isBlank());

    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            Mockito.mock(com.healflow.platform.repository.IncidentRepository.class),
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox-image",
            "ai-image",
            "");

    try {
      System.setProperty("user.home", home.toString());

      writeClaudeSettings(home, "{\"env\":{\"ANTHROPIC_API_KEY\":\"k1\"}}");
      assertEquals("k1", invokeGetApiKeyFromHost(service));

      writeClaudeSettings(home, "{\"env\":{\"anthropic_api_key\":\"k2\"}}");
      assertEquals("k2", invokeGetApiKeyFromHost(service));

      writeClaudeSettings(home, "{\"ANTHROPIC_API_KEY\":\"k3\"}");
      assertEquals("k3", invokeGetApiKeyFromHost(service));

      writeClaudeSettings(home, "{\"apiKey\":\"k4\"}");
      assertEquals("k4", invokeGetApiKeyFromHost(service));
    } finally {
      if (previousUserHome == null) {
        System.clearProperty("user.home");
      } else {
        System.setProperty("user.home", previousUserHome);
      }
    }
  }

  @Test
  void truncateHandlesNullShortAndLong() throws Exception {
    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    FingerprintService fingerprintService = Mockito.mock(FingerprintService.class);
    IncidentService service =
        new IncidentService(
            gitManager,
            dockerSandboxManager,
            Mockito.mock(com.healflow.platform.repository.IncidentRepository.class),
            fingerprintService,
            Mockito.mock(ApplicationService.class),
            "sandbox-image",
            "ai-image",
            "");

    assertEquals("", invokeTruncate(service, null, 10));
    assertEquals("abc", invokeTruncate(service, "abc", 10));
    assertEquals("abcde...", invokeTruncate(service, "abcdefghij", 5));
  }

  private static void writeClaudeSettings(Path home, String json) throws Exception {
    Path claudeDir = Files.createDirectories(home.resolve(".claude"));
    Files.writeString(claudeDir.resolve("settings.json"), json);
  }

  private static IncidentEntity invokeFindOrCreateIncident(
      IncidentService service, String incidentId, IncidentReport report) throws Exception {
    java.lang.reflect.Method method =
        IncidentService.class.getDeclaredMethod("findOrCreateIncident", String.class, IncidentReport.class);
    method.setAccessible(true);
    return (IncidentEntity) method.invoke(service, incidentId, report);
  }

  private static String invokeGetApiKeyFromHost(IncidentService service) throws Exception {
    java.lang.reflect.Method method = IncidentService.class.getDeclaredMethod("getApiKeyFromHost");
    method.setAccessible(true);
    return (String) method.invoke(service);
  }

  private static String invokeTruncate(IncidentService service, String text, int maxLength) throws Exception {
    java.lang.reflect.Method method =
        IncidentService.class.getDeclaredMethod("truncate", String.class, int.class);
    method.setAccessible(true);
    return (String) method.invoke(service, text, maxLength);
  }

  private static String invokeSanitizeContainerNameComponent(String value) throws Exception {
    java.lang.reflect.Method method =
        IncidentService.class.getDeclaredMethod("sanitizeContainerNameComponent", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, value);
  }

  private static String invokeBuildContainerName(String appId) throws Exception {
    java.lang.reflect.Method method = IncidentService.class.getDeclaredMethod("buildContainerName", String.class);
    method.setAccessible(true);
    return (String) method.invoke(null, appId);
  }
}

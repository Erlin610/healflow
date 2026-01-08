package com.healflow.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.healflow.common.dto.IncidentReport;
import com.healflow.engine.HealingResult;
import com.healflow.engine.HealflowEngine;
import com.healflow.engine.Severity;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellRunner;
import com.healflow.engine.shell.ShellTimeoutException;
import com.healflow.platform.controller.IncidentController;
import com.healflow.platform.controller.ReportController;
import com.healflow.platform.controller.ReportRequest;
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
      "healflow.sandbox.image=test-image"
    })
class IncidentServiceTest {

  @Autowired private IncidentService incidentService;
  @MockBean private GitWorkspaceManager gitManager;
  @MockBean private ShellRunner shellRunner;

  @Test
  void processIncidentRunsAsyncAndCallsGitManager(@TempDir Path workspace) throws Exception {
    Files.writeString(workspace.resolve("mock-agent.sh"), "#!/bin/bash\necho ok\n");

    IncidentReport report =
        new IncidentReport(
            "app-123",
            "https://example.invalid/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "stack",
            Map.of("env", "test"),
            Instant.parse("2026-01-05T00:00:00Z"));

    String callingThread = Thread.currentThread().getName();
    AtomicReference<String> invokedThread = new AtomicReference<>();
    CountDownLatch gitLatch = new CountDownLatch(1);
    CountDownLatch cleanupLatch = new CountDownLatch(1);
    AtomicReference<ShellCommand> dockerRunCommand = new AtomicReference<>();
    AtomicReference<List<String>> dockerRunArgv = new AtomicReference<>();
    AtomicReference<List<String>> dockerRmArgv = new AtomicReference<>();

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenAnswer(
            invocation -> {
              invokedThread.set(Thread.currentThread().getName());
              gitLatch.countDown();
              return workspace;
            });

    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                dockerRunCommand.set(command);
                dockerRunArgv.set(argv);
                return new CommandResult(0, "ok");
              }
              if (argv.size() >= 2 && argv.get(1).equals("rm")) {
                dockerRmArgv.set(argv);
                cleanupLatch.countDown();
                return new CommandResult(0, "");
              }
              return new CommandResult(0, "");
            });

    incidentService.processIncident(report);

    assertTrue(gitLatch.await(2, TimeUnit.SECONDS));
    assertTrue(cleanupLatch.await(2, TimeUnit.SECONDS));
    assertNotEquals(callingThread, invokedThread.get());
    verify(gitManager).prepareWorkspace(report.appId(), report.repoUrl(), report.branch());

    List<String> runArgv = dockerRunArgv.get();
    assertTrue(runArgv.contains("run"));
    assertTrue(runArgv.contains("--name"));
    int nameIndex = runArgv.indexOf("--name") + 1;
    String containerName = runArgv.get(nameIndex);
    assertTrue(containerName.startsWith("healflow-sandbox-app-123-"));
    assertTrue(runArgv.contains("-v"));
    int volumeIndex = runArgv.indexOf("-v") + 1;
    assertTrue(runArgv.get(volumeIndex).endsWith(":/src"));
    assertTrue(runArgv.contains("test-image"));
    assertTrue(runArgv.containsAll(List.of("bash", "/src/mock-agent.sh")));

    ShellCommand runCommand = dockerRunCommand.get();
    assertTrue(runCommand.interactions().size() >= 4);

    List<String> rmArgv = dockerRmArgv.get();
    assertTrue(rmArgv.contains("rm"));
    assertTrue(rmArgv.contains("-f"));
    assertTrue(rmArgv.contains(containerName));
  }

  @Test
  void processIncidentDefaultsToEmptyEnvironmentWhenNull(@TempDir Path workspace) throws Exception {
    Files.writeString(workspace.resolve("mock-agent.sh"), "#!/bin/bash\necho ok\n");

    IncidentReport report =
        new IncidentReport(
            "app-123",
            "https://example.invalid/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "stack",
            null,
            Instant.parse("2026-01-05T00:00:00Z"));

    CountDownLatch cleanupLatch = new CountDownLatch(1);
    AtomicReference<List<String>> dockerRunArgv = new AtomicReference<>();

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenReturn(workspace);
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                dockerRunArgv.set(argv);
                return new CommandResult(0, "ok");
              }
              if (argv.size() >= 2 && argv.get(1).equals("rm")) {
                cleanupLatch.countDown();
                return new CommandResult(0, "");
              }
              return new CommandResult(0, "");
            });

    incidentService.processIncident(report);

    assertTrue(cleanupLatch.await(2, TimeUnit.SECONDS));
    assertTrue(!dockerRunArgv.get().contains("-e"));
  }

  @Test
  void processIncidentSwallowsFailures() throws Exception {
    IncidentReport report =
        new IncidentReport(
            "app-456",
            "https://example.invalid/repo.git",
            "main",
            "IllegalStateException",
            "boom",
            "stack",
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    String callingThread = Thread.currentThread().getName();
    AtomicReference<String> invokedThread = new AtomicReference<>();
    CountDownLatch latch = new CountDownLatch(1);

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenAnswer(
            invocation -> {
              invokedThread.set(Thread.currentThread().getName());
              latch.countDown();
              throw new RuntimeException("boom");
            });

    incidentService.processIncident(report);

    assertTrue(latch.await(2, TimeUnit.SECONDS));
    assertNotEquals(callingThread, invokedThread.get());
    verify(gitManager).prepareWorkspace(report.appId(), report.repoUrl(), report.branch());
    verify(shellRunner, never()).run(any(ShellCommand.class));
  }

  @Test
  void processIncidentCleansUpContainerWhenRunFails(@TempDir Path workspace) throws Exception {
    Files.writeString(workspace.resolve("mock-agent.sh"), "#!/bin/bash\necho ok\n");

    IncidentReport report =
        new IncidentReport(
            "app 123",
            "https://example.invalid/repo.git",
            "main",
            "RuntimeException",
            "boom",
            "stack",
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    CountDownLatch cleanupLatch = new CountDownLatch(1);

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenReturn(workspace);
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                return new CommandResult(1, "mock agent failed");
              }
              if (argv.size() >= 2 && argv.get(1).equals("rm")) {
                cleanupLatch.countDown();
                return new CommandResult(0, "");
              }
              return new CommandResult(0, "");
            });

    incidentService.processIncident(report);

    assertTrue(cleanupLatch.await(2, TimeUnit.SECONDS));
    verify(shellRunner).run(argThat(command -> command.argv().size() >= 2 && command.argv().get(1).equals("rm")));
  }

  @Test
  void processIncidentCleansUpContainerWhenRunTimesOut(@TempDir Path workspace) throws Exception {
    Files.writeString(workspace.resolve("mock-agent.sh"), "#!/bin/bash\necho ok\n");

    IncidentReport report =
        new IncidentReport(
            "app 123",
            "https://example.invalid/repo.git",
            "main",
            "RuntimeException",
            "boom",
            "stack",
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    CountDownLatch cleanupLatch = new CountDownLatch(1);
    AtomicReference<List<String>> dockerRunArgv = new AtomicReference<>();

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenReturn(workspace);
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                dockerRunArgv.set(argv);
                throw new ShellTimeoutException(command, "partial output");
              }
              if (argv.size() >= 2 && argv.get(1).equals("rm")) {
                cleanupLatch.countDown();
                return new CommandResult(0, "");
              }
              return new CommandResult(0, "");
            });

    incidentService.processIncident(report);

    assertTrue(cleanupLatch.await(2, TimeUnit.SECONDS));
    List<String> runArgv = dockerRunArgv.get();
    assertTrue(runArgv.contains("--name"));
    String containerName = runArgv.get(runArgv.indexOf("--name") + 1);
    assertTrue(containerName.startsWith("healflow-sandbox-app-123-"));
    verify(shellRunner).run(argThat(command -> command.argv().size() >= 2 && command.argv().get(1).equals("rm")));
  }

  @Test
  void processIncidentCleansUpContainerWhenSandboxOoms(@TempDir Path workspace) throws Exception {
    Files.writeString(workspace.resolve("mock-agent.sh"), "#!/bin/bash\necho ok\n");

    IncidentReport report =
        new IncidentReport(
            "app-oom",
            "https://example.invalid/repo.git",
            "main",
            "RuntimeException",
            "boom",
            "stack",
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    CountDownLatch cleanupLatch = new CountDownLatch(1);

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenReturn(workspace);
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                return new CommandResult(137, "Killed");
              }
              if (argv.size() >= 2 && argv.get(1).equals("rm")) {
                cleanupLatch.countDown();
                return new CommandResult(0, "");
              }
              return new CommandResult(0, "");
            });

    incidentService.processIncident(report);

    assertTrue(cleanupLatch.await(2, TimeUnit.SECONDS));
    verify(shellRunner).run(argThat(command -> command.argv().size() >= 2 && command.argv().get(1).equals("rm")));
  }

  @Test
  void processIncidentDoesNotStartDockerWhenScriptMissing(@TempDir Path workspace) throws Exception {
    IncidentReport report =
        new IncidentReport(
            " ",
            "https://example.invalid/repo.git",
            "main",
            "RuntimeException",
            "boom",
            "stack",
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    CountDownLatch gitLatch = new CountDownLatch(1);
    CountDownLatch dockerLatch = new CountDownLatch(1);

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenAnswer(
            invocation -> {
              gitLatch.countDown();
              return workspace;
            });
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              dockerLatch.countDown();
              return new CommandResult(0, "unexpected");
            });

    incidentService.processIncident(report);

    assertTrue(gitLatch.await(2, TimeUnit.SECONDS));
    assertTrue(!dockerLatch.await(250, TimeUnit.MILLISECONDS));
    verify(shellRunner, never()).run(any(ShellCommand.class));
  }

  @Test
  void reportRequestStoresIncidentReport() {
    ReportRequest request = new ReportRequest("boom");
    assertEquals("boom", request.incidentReport());
  }

  @Test
  void incidentControllerDelegatesAndReturnsReceived() {
    IncidentService delegate = Mockito.mock(IncidentService.class);
    IncidentController controller = new IncidentController(delegate, Mockito.mock(GitWorkspaceManager.class));
    IncidentReport report =
        new IncidentReport(
            "app-789",
            "https://example.invalid/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "stack",
            Map.of(),
            Instant.parse("2026-01-05T00:00:00Z"));

    assertEquals("RECEIVED", controller.receiveReport(report));
    verify(delegate).processIncident(report);
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
}

class IncidentServicePhase5Test {

  @Test
  void analyzeIncidentParsesSandboxJsonAndPassesPrompt(@TempDir Path tempDir) throws Exception {
    Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
    Path home = Files.createDirectories(tempDir.resolve("home"));
    String previousUserHome = System.getProperty("user.home");

    com.healflow.engine.git.GitWorkspaceManager gitManager = Mockito.mock(com.healflow.engine.git.GitWorkspaceManager.class);
    com.healflow.engine.sandbox.DockerSandboxManager dockerSandboxManager =
        Mockito.mock(com.healflow.engine.sandbox.DockerSandboxManager.class);
    IncidentService service = new IncidentService(gitManager, dockerSandboxManager, "sandbox-image", "ai-image");

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

      com.healflow.common.dto.AnalysisResult result = service.analyzeIncident(report);
      assertEquals("sess-123", result.sessionId());
      assertEquals(structuredOutput, result.structuredOutput());
      assertEquals("analysis", result.fullText());

      var environmentCaptor = org.mockito.ArgumentCaptor.forClass(Map.class);
      var argvCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
      verify(dockerSandboxManager)
          .executeInteractiveRunInSandbox(
              argThat(name -> name.startsWith("healflow-sandbox-app-123-")),
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
      assertTrue(argv.contains("--output-format"));
      assertTrue(argv.contains("json"));
      assertTrue(argv.contains("--allowedTools"));
      assertTrue(argv.contains("Read,Grep,Glob"));
      String prompt = argv.get(2);
      assertTrue(prompt.contains(stackTrace.substring(0, 1000) + "..."));
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
    IncidentService service = new IncidentService(gitManager, dockerSandboxManager, "sandbox-image", "ai-image");

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
              argThat(name -> name.startsWith("healflow-sandbox-fix-gen-")),
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
      assertTrue(argv.contains("Read"));
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
    IncidentService service = new IncidentService(gitManager, dockerSandboxManager, "sandbox-image", "ai-image");

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
              argThat(name -> name.startsWith("healflow-sandbox-fix-apply-")),
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
    IncidentService service = new IncidentService(gitManager, dockerSandboxManager, "sandbox-image", "ai-image");

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
    IncidentService service = new IncidentService(gitManager, dockerSandboxManager, "sandbox-image", "ai-image");

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
    IncidentService service = new IncidentService(gitManager, dockerSandboxManager, "sandbox-image", "ai-image");

    assertEquals("", invokeTruncate(service, null, 10));
    assertEquals("abc", invokeTruncate(service, "abc", 10));
    assertEquals("abcde...", invokeTruncate(service, "abcdefghij", 5));
  }

  private static void writeClaudeSettings(Path home, String json) throws Exception {
    Path claudeDir = Files.createDirectories(home.resolve(".claude"));
    Files.writeString(claudeDir.resolve("settings.json"), json);
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
}

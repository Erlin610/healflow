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
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
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
  void processIncidentRunsAsyncAndCallsGitManager() throws Exception {
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
    AtomicReference<List<String>> dockerRunArgv = new AtomicReference<>();
    AtomicReference<List<String>> dockerExecArgv = new AtomicReference<>();
    AtomicReference<List<String>> dockerRmArgv = new AtomicReference<>();

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenAnswer(
            invocation -> {
              invokedThread.set(Thread.currentThread().getName());
              gitLatch.countDown();
              return Path.of("E:\\mine\\healflow\\build\\tmp");
            });

    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                dockerRunArgv.set(argv);
                return new CommandResult(0, "container-1\n");
              }
              if (argv.size() >= 2 && argv.get(1).equals("exec")) {
                dockerExecArgv.set(argv);
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

    List<String> execArgv = dockerExecArgv.get();
    assertTrue(execArgv.contains("exec"));
    assertTrue(execArgv.contains(containerName));
    assertTrue(execArgv.containsAll(List.of("ls", "-al", "/src")));

    List<String> rmArgv = dockerRmArgv.get();
    assertTrue(rmArgv.contains("rm"));
    assertTrue(rmArgv.contains("-f"));
    assertTrue(rmArgv.contains(containerName));
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
  void processIncidentCleansUpContainerWhenExecFails() throws Exception {
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
        .thenReturn(Path.of("E:\\mine\\healflow\\build\\tmp"));
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                return new CommandResult(0, "container-2\n");
              }
              if (argv.size() >= 2 && argv.get(1).equals("exec")) {
                return new CommandResult(1, "exec failed");
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
  void processIncidentCleansUpContainerWhenExecTimesOut() throws Exception {
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
        .thenReturn(Path.of("E:\\mine\\healflow\\build\\tmp"));
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                dockerRunArgv.set(argv);
                return new CommandResult(0, "container-3\n");
              }
              if (argv.size() >= 2 && argv.get(1).equals("exec")) {
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
  void processIncidentCleansUpContainerWhenSandboxOoms() throws Exception {
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
        .thenReturn(Path.of("E:\\mine\\healflow\\build\\tmp"));
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                return new CommandResult(0, "container-oom\n");
              }
              if (argv.size() >= 2 && argv.get(1).equals("exec")) {
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
  void processIncidentSkipsCleanupWhenContainerFailsToStart() throws Exception {
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

    CountDownLatch startLatch = new CountDownLatch(1);

    when(gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch()))
        .thenReturn(Path.of("E:\\mine\\healflow\\build\\tmp"));
    when(shellRunner.run(any(ShellCommand.class)))
        .thenAnswer(
            invocation -> {
              ShellCommand command = invocation.getArgument(0);
              List<String> argv = command.argv();
              if (argv.size() >= 2 && argv.get(1).equals("run")) {
                startLatch.countDown();
                return new CommandResult(1, "docker down");
              }
              return new CommandResult(0, "");
            });

    incidentService.processIncident(report);

    assertTrue(startLatch.await(2, TimeUnit.SECONDS));
    verify(shellRunner, never())
        .run(argThat(command -> command.argv().size() >= 2 && command.argv().get(1).equals("rm")));
  }

  @Test
  void reportRequestStoresIncidentReport() {
    ReportRequest request = new ReportRequest("boom");
    assertEquals("boom", request.incidentReport());
  }

  @Test
  void incidentControllerDelegatesAndReturnsReceived() {
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

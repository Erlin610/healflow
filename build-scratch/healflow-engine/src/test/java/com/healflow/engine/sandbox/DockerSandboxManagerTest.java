package com.healflow.engine.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.InteractionRule;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellExecutionException;
import com.healflow.engine.shell.ShellTimeoutException;
import com.healflow.engine.shell.ShellRunner;
import com.healflow.engine.testsupport.FakeShellRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class DockerSandboxManagerTest {

  private static final class RecordingShellRunner implements ShellRunner {
    private final Deque<Object> steps = new ArrayDeque<>();
    private final List<ShellCommand> commands = new ArrayList<>();

    void enqueueResult(CommandResult result) {
      steps.add(result);
    }

    void enqueueFailure(RuntimeException failure) {
      steps.add(failure);
    }

    List<ShellCommand> commands() {
      return commands;
    }

    @Override
    public CommandResult run(ShellCommand command) {
      commands.add(command);
      if (steps.isEmpty()) {
        throw new IllegalStateException("No queued result for command: " + command.argv());
      }
      Object step = steps.removeFirst();
      if (step instanceof RuntimeException) {
        throw (RuntimeException) step;
      }
      return (CommandResult) step;
    }
  }

  @Test
  void buildsDockerRunExecAndRmCommands() {
    FakeShellRunner runner = new FakeShellRunner();
    runner.enqueueResult(new CommandResult(0, "container123\n"));
    runner.enqueueResult(new CommandResult(0, "ok\n"));
    runner.enqueueResult(new CommandResult(0, ""));

    DockerSandboxManager manager = new DockerSandboxManager(runner);

    LinkedHashMap<String, String> env = new LinkedHashMap<>();
    env.put("A", "B");

    String id =
        manager.startDetached("task-1", Path.of("C:\\workspace"), "/container/src", "my-image:latest", env);
    assertEquals("container123", id);
    assertTrue(runner.lastCommand().argv().containsAll(List.of("docker", "run", "-d", "--name", "task-1")));
    assertTrue(runner.lastCommand().argv().containsAll(List.of("-w", "/container/src")));

    CommandResult execResult =
        manager.exec(
            "task-1",
            List.of("echo", "hi"),
            Duration.ofSeconds(5),
            List.of(new InteractionRule(Pattern.compile("hi"), "y", 1)));
    assertEquals(0, execResult.exitCode());

    manager.removeForce("task-1");
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.lastCommand().argv());
  }

  @Test
  void executeInSandboxRunsLifecycleAndReturnsExecResult() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, "container123\n"));
    runner.enqueueResult(new CommandResult(0, "ok\n"));
    runner.enqueueResult(new CommandResult(0, ""));

    DockerSandboxManager manager = new DockerSandboxManager(runner);
    CommandResult result =
        manager.executeInSandbox(
            "task-1",
            Path.of("C:\\workspace"),
            "/container/src",
            "my-image:latest",
            Map.of("A", "B"),
            List.of("echo", "hi"),
            Duration.ofSeconds(5),
            List.of());

    assertEquals(0, result.exitCode());
    assertEquals("ok\n", result.output());
    assertEquals(3, runner.commands().size());
    assertTrue(
        runner
            .commands()
            .get(0)
            .argv()
            .containsAll(List.of("docker", "run", "-d", "--name", "task-1")));
    assertEquals(List.of("docker", "exec", "-i", "task-1", "echo", "hi"), runner.commands().get(1).argv());
    assertEquals(Duration.ofSeconds(5), runner.commands().get(1).timeout());
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(2).argv());
  }

  @Test
  void executeInSandboxAlwaysRemovesContainerOnExecFailure() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, "container123\n"));
    runner.enqueueResult(new CommandResult(2, "bad"));
    runner.enqueueResult(new CommandResult(0, ""));

    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.executeInSandbox(
                    "task-1",
                    Path.of("C:\\workspace"),
                    "/container/src",
                    "my-image:latest",
                    Map.of(),
                    List.of("echo", "hi"),
                    Duration.ofSeconds(5),
                    List.of()));

    assertTrue(ex.details().contains("bad"));
    assertEquals(3, runner.commands().size());
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(2).argv());
  }

  @Test
  void executeInSandboxAddsCleanupFailureAsSuppressed() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, "container123\n"));
    runner.enqueueResult(new CommandResult(2, "bad"));
    runner.enqueueResult(new CommandResult(1, "rm-bad"));

    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.executeInSandbox(
                    "task-1",
                    Path.of("C:\\workspace"),
                    "/container/src",
                    "my-image:latest",
                    Map.of(),
                    List.of("echo", "hi"),
                    Duration.ofSeconds(5),
                    List.of()));

    assertTrue(ex.details().contains("bad"));
    assertEquals(1, ex.getSuppressed().length);
    assertTrue(ex.getSuppressed()[0] instanceof SandboxException);
    assertTrue(((SandboxException) ex.getSuppressed()[0]).details().contains("rm-bad"));
  }

  @Test
  void executeInSandboxPropagatesCleanupFailureAfterSuccess() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, "container123\n"));
    runner.enqueueResult(new CommandResult(0, "ok\n"));
    runner.enqueueResult(new CommandResult(1, "rm-bad"));

    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.executeInSandbox(
                    "task-1",
                    Path.of("C:\\workspace"),
                    "/container/src",
                    "my-image:latest",
                    Map.of(),
                    List.of("echo", "hi"),
                    Duration.ofSeconds(5),
                    List.of()));

    assertTrue(ex.details().contains("rm-bad"));
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(2).argv());
  }

  @Test
  void executeInSandboxRejectsEmptyArgvWithoutStartingContainer() {
    ShellRunner runner =
        command -> {
          throw new AssertionError("ShellRunner should not be called");
        };

    DockerSandboxManager manager = new DockerSandboxManager(runner);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.executeInSandbox(
                "task-1", Path.of("C:\\workspace"), "/container/src", "my-image:latest", List.of()));
  }

  @Test
  void surfacesNonZeroExitCodes() {
    FakeShellRunner runner = new FakeShellRunner();
    runner.enqueueResult(new CommandResult(1, "boom"));
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.startDetached(
                    "task-1", Path.of("C:\\workspace"), "/container/src", "my-image:latest", new LinkedHashMap<>()));
    assertTrue(ex.details().contains("boom"));
  }

  @Test
  void rejectsUnsafeContainerNames() {
    DockerSandboxManager manager = new DockerSandboxManager(new FakeShellRunner());
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.exec(
                "../x", List.of("echo", "hi"), Duration.ofSeconds(1), List.of()));
  }

  @Test
  void rejectsEmptyContainerIdFromDockerRun() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, " \n"));
    runner.enqueueResult(new CommandResult(0, ""));
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    assertThrows(
        SandboxException.class,
        () ->
            manager.startDetached(
                "task-1", Path.of("C:\\workspace"), "/container/src", "my-image:latest", new LinkedHashMap<>()));
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(1).argv());
  }

  @Test
  void attemptsCleanupIfDockerReturnsEmptyContainerId() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, " \n"));
    runner.enqueueResult(new CommandResult(1, "rm-bad"));
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.startDetached(
                    "task-1", Path.of("C:\\workspace"), "/container/src", "my-image:latest", new LinkedHashMap<>()));
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(1).argv());
    assertEquals(1, ex.getSuppressed().length);
    assertTrue(ex.getSuppressed()[0] instanceof SandboxException);
    assertTrue(((SandboxException) ex.getSuppressed()[0]).details().contains("rm-bad"));
  }

  @Test
  void execValidatesArgumentsAndDefaultsTimeout() {
    FakeShellRunner runner = new FakeShellRunner();
    runner.enqueueResult(new CommandResult(0, "ok\n"));
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    assertThrows(
        IllegalArgumentException.class,
        () -> manager.exec("task-1", List.of(), Duration.ofSeconds(1), List.of()));

    CommandResult result = manager.exec("task-1", List.of("echo", "hi"), null, List.of());
    assertEquals(0, result.exitCode());
    assertEquals(Duration.ofMinutes(2), runner.lastCommand().timeout());
  }

  @Test
  void executeInSandboxRejectsNegativeTimeoutWithoutStartingContainer() {
    ShellRunner runner =
        command -> {
          throw new AssertionError("ShellRunner should not be called");
        };

    DockerSandboxManager manager = new DockerSandboxManager(runner);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.executeInSandbox(
                "task-1",
                Path.of("C:\\workspace"),
                "/container/src",
                "my-image:latest",
                Map.of(),
                List.of("echo", "hi"),
                Duration.ofSeconds(-1),
                List.of()));
  }

  @Test
  void executeInSandboxDoesNotAttemptCleanupWhenStartDetachedFails() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(1, "boom"));
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    assertThrows(
        SandboxException.class,
        () ->
            manager.executeInSandbox(
                "task-1",
                Path.of("C:\\workspace"),
                "/container/src",
                "my-image:latest",
                Map.of(),
                List.of("echo", "hi"),
                Duration.ofSeconds(5),
                List.of()));
    assertEquals(1, runner.commands().size());
    assertTrue(runner.commands().get(0).argv().containsAll(List.of("docker", "run", "-d", "--name", "task-1")));
  }

  @Test
  void executeInSandboxRejectsBlankArgvWithoutStartingContainer() {
    ShellRunner runner =
        command -> {
          throw new AssertionError("ShellRunner should not be called");
        };

    DockerSandboxManager manager = new DockerSandboxManager(runner);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            manager.executeInSandbox(
                "task-1",
                Path.of("C:\\workspace"),
                "/container/src",
                "my-image:latest",
                Map.of(),
                List.of(" "),
                Duration.ofSeconds(5),
                List.of()));
  }

  @Test
  void wrapsShellExecutionExceptions() {
    ShellRunner runner =
        command -> {
          throw new ShellExecutionException(command, "boom");
        };

    DockerSandboxManager manager = new DockerSandboxManager(runner);
    SandboxException ex = assertThrows(SandboxException.class, () -> manager.removeForce("task-1"));
    assertTrue(ex.details().contains("docker rm -f task-1"));
  }

  @Test
  void executeInSandboxRemovesContainerWhenExecTimesOut() {
    class TimeoutShellRunner implements ShellRunner {
      private final List<ShellCommand> commands = new ArrayList<>();

      List<ShellCommand> commands() {
        return commands;
      }

      @Override
      public CommandResult run(ShellCommand command) {
        commands.add(command);
        List<String> argv = command.argv();
        if (argv.size() >= 2 && argv.get(1).equals("run")) {
          return new CommandResult(0, "container123\n");
        }
        if (argv.size() >= 2 && argv.get(1).equals("exec")) {
          throw new ShellTimeoutException(command, "partial output");
        }
        if (argv.size() >= 2 && argv.get(1).equals("rm")) {
          return new CommandResult(0, "");
        }
        throw new AssertionError("Unexpected docker command: " + argv);
      }
    }

    TimeoutShellRunner runner = new TimeoutShellRunner();
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.executeInSandbox(
                    "task-1",
                    Path.of("C:\\workspace"),
                    "/container/src",
                    "my-image:latest",
                    Map.of(),
                    List.of("echo", "hi"),
                    Duration.ofSeconds(1),
                    List.of()));

    assertInstanceOf(ShellTimeoutException.class, ex.getCause());
    assertEquals(3, runner.commands().size());
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(2).argv());
  }

  @Test
  void executeInteractiveRunInSandboxRunsDockerRunIAndCleansUp() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, "ok\n"));
    runner.enqueueResult(new CommandResult(0, ""));

    DockerSandboxManager manager = new DockerSandboxManager(runner);

    Map<String, String> env = new LinkedHashMap<>();
    env.put("A", "B");

    CommandResult result =
        manager.executeInteractiveRunInSandbox(
            "task-1",
            Path.of("C:\\workspace"),
            "/container/src",
            "my-image:latest",
            env,
            List.of("echo", "hi"),
            null,
            List.of());

    assertEquals(0, result.exitCode());
    assertEquals("ok\n", result.output());
    assertEquals(2, runner.commands().size());

    ShellCommand runCommand = runner.commands().get(0);
    assertTrue(runCommand.argv().containsAll(List.of("docker", "run", "-i", "--name", "task-1")));
    assertTrue(runCommand.argv().containsAll(List.of("-w", "/container/src")));
    assertTrue(runCommand.argv().containsAll(List.of("-e", "A=B")));
    assertEquals(Duration.ofMinutes(2), runCommand.timeout());

    List<InteractionRule> interactions = runCommand.interactions();
    assertFalse(interactions.isEmpty());
    assertTrue(containsEquivalent(interactions, Pattern.compile("(?i)\\[\\s*y\\s*/\\s*n\\s*\\]"), "y"));

    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(1).argv());
  }

  @Test
  void executeInteractiveRunInSandboxAlwaysRemovesContainerOnFailure() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(2, "bad"));
    runner.enqueueResult(new CommandResult(0, ""));

    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.executeInteractiveRunInSandbox(
                    "task-1",
                    Path.of("C:\\workspace"),
                    "/container/src",
                    "my-image:latest",
                    Map.of(),
                    List.of("echo", "hi"),
                    Duration.ofSeconds(1),
                    List.of()));

    assertTrue(ex.details().contains("bad"));
    assertEquals(2, runner.commands().size());
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(1).argv());
  }

  @Test
  void executeInteractiveRunInSandboxMergesAutoApproveRules() {
    RecordingShellRunner runner = new RecordingShellRunner();
    runner.enqueueResult(new CommandResult(0, "ok\n"));
    runner.enqueueResult(new CommandResult(0, ""));

    DockerSandboxManager manager = new DockerSandboxManager(runner);
    InteractionRule custom = new InteractionRule(Pattern.compile("prompt"), "custom", 1);

    manager.executeInteractiveRunInSandbox(
        "task-1",
        Path.of("C:\\workspace"),
        "/container/src",
        "my-image:latest",
        Map.of(),
        List.of("echo", "hi"),
        Duration.ofSeconds(1),
        List.of(custom));

    List<InteractionRule> interactions = runner.commands().get(0).interactions();
    assertTrue(interactions.size() > 1);
    assertEquals("custom", interactions.get(0).response());
    assertTrue(containsEquivalent(interactions, Pattern.compile("(?i)\\(\\s*y\\s*/\\s*n\\s*\\)"), "y"));
  }

  @Test
  void executeInteractiveRunInSandboxRemovesContainerWhenRunTimesOut() {
    class TimeoutShellRunner implements ShellRunner {
      private final List<ShellCommand> commands = new ArrayList<>();

      List<ShellCommand> commands() {
        return commands;
      }

      @Override
      public CommandResult run(ShellCommand command) {
        commands.add(command);
        List<String> argv = command.argv();
        if (argv.size() >= 2 && argv.get(1).equals("run")) {
          throw new ShellTimeoutException(command, "partial output");
        }
        if (argv.size() >= 2 && argv.get(1).equals("rm")) {
          return new CommandResult(0, "");
        }
        throw new AssertionError("Unexpected docker command: " + argv);
      }
    }

    TimeoutShellRunner runner = new TimeoutShellRunner();
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    SandboxException ex =
        assertThrows(
            SandboxException.class,
            () ->
                manager.executeInteractiveRunInSandbox(
                    "task-1",
                    Path.of("C:\\workspace"),
                    "/container/src",
                    "my-image:latest",
                    Map.of(),
                    List.of("echo", "hi"),
                    Duration.ofSeconds(1),
                    List.of()));

    assertInstanceOf(ShellTimeoutException.class, ex.getCause());
    assertEquals(2, runner.commands().size());
    assertEquals(List.of("docker", "rm", "-f", "task-1"), runner.commands().get(1).argv());
  }

  private static boolean containsEquivalent(List<InteractionRule> rules, Pattern pattern, String response) {
    for (InteractionRule rule : rules) {
      if (!rule.response().equals(response)) {
        continue;
      }
      Pattern existing = rule.pattern();
      if (existing.flags() == pattern.flags() && existing.pattern().equals(pattern.pattern())) {
        return true;
      }
    }
    return false;
  }
}

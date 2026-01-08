package com.healflow.engine.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.InteractionRule;
import com.healflow.engine.testsupport.FakeShellRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Superseded by DockerSandboxManagerTest")
class DockerSandboxManagerSandboxTest {

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
    FakeShellRunner runner = new FakeShellRunner();
    runner.enqueueResult(new CommandResult(0, " \n"));
    DockerSandboxManager manager = new DockerSandboxManager(runner);

    assertThrows(
        SandboxException.class,
        () ->
            manager.startDetached(
                "task-1", Path.of("C:\\workspace"), "/container/src", "my-image:latest", new LinkedHashMap<>()));
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
}

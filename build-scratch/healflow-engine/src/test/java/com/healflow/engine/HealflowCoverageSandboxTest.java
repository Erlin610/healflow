package com.healflow.engine;

import static org.junit.jupiter.api.Assertions.*;

import com.healflow.engine.git.GitException;
import com.healflow.engine.sandbox.SandboxException;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellExecutionException;
import com.healflow.engine.shell.ShellTimeoutException;
import com.healflow.engine.workspace.WorkspaceException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HealflowCoverageSandboxTest {

  @Test
  void coversEngineBasics() {
    SimpleHealflowEngine defaultEngine = new SimpleHealflowEngine();
    assertEquals(Severity.HIGH, defaultEngine.analyze("panic").severity());

    SimpleHealflowEngine engine = new SimpleHealflowEngine("panic");
    HealingResult high = engine.analyze("Kernel PANIC: oops");
    assertEquals(Severity.HIGH, high.severity());
    assertEquals("triage_required", high.message());

    HealingResult low = engine.analyze("all good");
    assertEquals(Severity.LOW, low.severity());
    assertEquals("no_action", low.message());
  }

  @Test
  void coversUtilityTypes() {
    CommandResult ok = new CommandResult(0, "out");
    assertTrue(ok.isSuccess());
    CommandResult fail = new CommandResult(2, "");
    assertFalse(fail.isSuccess());

    GitException git = new GitException("m", "d");
    assertEquals("d", git.details());
    GitException gitWithCause = new GitException("m", "d", new RuntimeException("x"));
    assertEquals("d", gitWithCause.details());

    WorkspaceException ws = new WorkspaceException("m", "d");
    assertEquals("d", ws.details());
    WorkspaceException wsWithCause = new WorkspaceException("m", "d", new RuntimeException("x"));
    assertEquals("d", wsWithCause.details());

    SandboxException sb = new SandboxException("m", "d");
    assertEquals("d", sb.details());
    SandboxException sbWithCause = new SandboxException("m", "d", new RuntimeException("x"));
    assertEquals("d", sbWithCause.details());

    ShellCommand command = new ShellCommand(List.of("x"), null, Duration.ofSeconds(1), Map.of(), List.of());
    ShellExecutionException sh = new ShellExecutionException(command, "boom");
    assertSame(command, sh.command());
    ShellExecutionException shWithCause = new ShellExecutionException(command, new RuntimeException("x"));
    assertSame(command, shWithCause.command());

    ShellTimeoutException timeout = new ShellTimeoutException(command, "partial");
    assertEquals("partial", timeout.outputSoFar());
  }
}

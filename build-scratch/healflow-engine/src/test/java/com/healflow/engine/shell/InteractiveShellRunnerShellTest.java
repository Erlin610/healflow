package com.healflow.engine.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class InteractiveShellRunnerSandboxCoverageTest {

  @Test
  void runsCommandAndCapturesOutput() {
    ScriptedPromptProcess process = new ScriptedPromptProcess();
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, command -> process);
    ShellCommand command =
        new ShellCommand(
            List.of("ignored"),
            null,
            Duration.ofSeconds(10),
            Map.of(),
            List.of(new InteractionRule(Pattern.compile("\\(y/n\\)"), "y", 1)));

    CommandResult result = runner.run(command);
    assertEquals(0, result.exitCode());
    assertTrue(result.output().contains("ACK"));
    assertEquals("y" + System.lineSeparator(), process.stdinCapture.toString(StandardCharsets.UTF_8));
  }

  @Test
  void supportsEnvironmentVariables() {
    CapturingStarter starter = new CapturingStarter(new CompletedProcess(0, "hello"));
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, starter);
    ShellCommand command =
        new ShellCommand(
            List.of("ignored"),
            null,
            Duration.ofSeconds(10),
            Map.of("HEALFLOW_TEST_ENV", "hello"),
            List.of());

    CommandResult result = runner.run(command);
    assertEquals(0, result.exitCode());
    assertEquals("hello", result.output());
    assertEquals("hello", starter.lastCommand.environment().get("HEALFLOW_TEST_ENV"));
  }

  @Test
  void timesOutWhenInteractionIsMissing() {
    HangingProcess hanging = new HangingProcess();
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, command -> hanging);
    ShellCommand command = new ShellCommand(List.of("ignored"), null, Duration.ofMillis(200), Map.of(), List.of());

    ShellTimeoutException ex = assertThrows(ShellTimeoutException.class, () -> runner.run(command));
    assertTrue(hanging.destroyed.get());
    assertEquals("", ex.outputSoFar());
  }

  @Test
  void timesOutWhenPromptDoesNotMatchAnyInteractionRule() {
    PromptingHangingProcess process = new PromptingHangingProcess("Continue? (y/n): ");
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, command -> process);
    ShellCommand command =
        new ShellCommand(
            List.of("ignored"),
            null,
            Duration.ofMillis(50),
            Map.of(),
            List.of(new InteractionRule(Pattern.compile("does-not-match"), "y", 1)));

    ShellTimeoutException ex = assertThrows(ShellTimeoutException.class, () -> runner.run(command));
    assertTrue(process.destroyed.get());
    assertTrue(ex.outputSoFar().contains("Continue?"));
  }

  @Test
  void validatesShellCommandArguments() {
    assertThrows(IllegalArgumentException.class, () -> new ShellCommand(List.of(), null, null, Map.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ShellCommand(List.of("  "), null, null, Map.of(), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ShellCommand(List.of("x"), null, null, Map.of(" ", "v"), List.of()));
    assertThrows(
        IllegalArgumentException.class,
        () -> new ShellCommand(List.of("x"), null, Duration.ofSeconds(-1), Map.of(), List.of()));

    InteractiveShellRunner runner = new InteractiveShellRunner();
    assertThrows(
        IllegalArgumentException.class,
        () -> runner.run(new ShellCommand(List.of("git", "--version"), null, Duration.ZERO, Map.of(), List.of())));

    assertThrows(
        IllegalArgumentException.class,
        () -> new InteractionRule(Pattern.compile("x"), "y", 0));
  }

  @Test
  void throwsWhenProcessStarterFails() {
    ProcessStarter starter =
        new ProcessStarter() {
          @Override
          public Process start(ShellCommand command) throws java.io.IOException {
            throw new java.io.IOException("nope");
          }
        };
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, starter);

    ShellExecutionException ex =
        assertThrows(
            ShellExecutionException.class,
            () -> runner.run(new ShellCommand(List.of("ignored"), null, Duration.ofSeconds(1), Map.of(), List.of())));
    assertNotNull(ex.command());
    assertNotNull(ex.getCause());
  }

  @Test
  void surfacesReaderFailureFromStdoutPump() {
    BrokenStdoutProcess process = new BrokenStdoutProcess();
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, command -> process);

    ShellExecutionException ex =
        assertThrows(
            ShellExecutionException.class,
            () -> runner.run(new ShellCommand(List.of("ignored"), null, Duration.ofSeconds(1), Map.of(), List.of())));
    assertNotNull(ex.command());
    assertNotNull(ex.getCause());
  }

  @Test
  void defaultProcessStarterPathIsExercisedEvenWhenBlocked() {
    InteractiveShellRunner runner = new InteractiveShellRunner();
    ShellCommand command =
        new ShellCommand(
            List.of("cmd.exe", "/c", "echo", "hi"),
            Path.of("."),
            Duration.ofSeconds(1),
            Map.of("HF_TEST_ENV", "1"),
            List.of());
    assertThrows(ShellExecutionException.class, () -> runner.run(command));
  }

  @Test
  void destroyHandlesInterruptedWaitAfterTimeout() {
    InterruptingWaitProcess process = new InterruptingWaitProcess();
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, command -> process);

    assertThrows(
        ShellTimeoutException.class,
        () -> runner.run(new ShellCommand(List.of("ignored"), null, Duration.ofMillis(10), Map.of(), List.of())));
    Thread.interrupted();
    assertTrue(process.destroyForciblyCalled.get());
  }

  @Test
  void ignoresInteractionWriteFailures() {
    BadStdinProcess process = new BadStdinProcess();
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, command -> process);

    CommandResult result =
        runner.run(
            new ShellCommand(
                List.of("ignored"),
                null,
                Duration.ofSeconds(1),
                Map.of(),
                List.of(new InteractionRule(Pattern.compile("\\(y/n\\)"), "y", 1))));
    assertEquals(0, result.exitCode());
    assertTrue(result.output().contains("Continue?"));
  }

  private static final class CapturingStarter implements ProcessStarter {

    private final Process process;
    private ShellCommand lastCommand;

    private CapturingStarter(Process process) {
      this.process = process;
    }

    @Override
    public Process start(ShellCommand command) {
      this.lastCommand = command;
      return process;
    }
  }

  private static final class CompletedProcess extends Process {

    private final int exitCode;
    private final ByteArrayInputStream output;
    private final ByteArrayOutputStream inputCapture = new ByteArrayOutputStream();

    private CompletedProcess(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return inputCapture;
    }

    @Override
    public InputStream getInputStream() {
      return output;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return exitCode;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return exitCode;
    }

    @Override
    public void destroy() {}
  }

  private static final class HangingProcess extends Process {

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
      Thread.sleep(Long.MAX_VALUE);
      return 1;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return false;
    }

    @Override
    public int exitValue() {
      throw new IllegalThreadStateException();
    }

    @Override
    public void destroy() {
      destroyed.set(true);
    }
  }

  private static final class PromptingHangingProcess extends Process {

    private final ByteArrayInputStream stdout;
    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    private PromptingHangingProcess(String prompt) {
      this.stdout = new ByteArrayInputStream(prompt.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return stdout;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
      Thread.sleep(Long.MAX_VALUE);
      return 1;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return false;
    }

    @Override
    public int exitValue() {
      throw new IllegalThreadStateException();
    }

    @Override
    public void destroy() {
      destroyed.set(true);
    }
  }

  private static final class ScriptedPromptProcess extends Process {

    private final ByteArrayOutputStream stdinCapture = new ByteArrayOutputStream();
    private final ByteArrayInputStream stdout;

    private ScriptedPromptProcess() {
      String output = "Continue? (y/n): ACK" + System.lineSeparator();
      this.stdout = new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return stdinCapture;
    }

    @Override
    public InputStream getInputStream() {
      return stdout;
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {}
  }

  private static final class BrokenStdoutProcess extends Process {

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return new InputStream() {
        @Override
        public int read() throws java.io.IOException {
          throw new java.io.IOException("boom");
        }
      };
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {}
  }

  private static final class InterruptingWaitProcess extends Process {

    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final AtomicBoolean destroyForciblyCalled = new AtomicBoolean(false);

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public InputStream getInputStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() throws InterruptedException {
      Thread.sleep(Long.MAX_VALUE);
      return 1;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      if (destroyed.get()) {
        throw new InterruptedException("boom");
      }
      return false;
    }

    @Override
    public int exitValue() {
      throw new IllegalThreadStateException();
    }

    @Override
    public void destroy() {
      destroyed.set(true);
    }

    @Override
    public Process destroyForcibly() {
      destroyForciblyCalled.set(true);
      destroyed.set(true);
      return this;
    }
  }

  private static final class BadStdinProcess extends Process {

    @Override
    public OutputStream getOutputStream() {
      return new OutputStream() {
        @Override
        public void write(int b) throws java.io.IOException {
          throw new java.io.IOException("nope");
        }
      };
    }

    @Override
    public InputStream getInputStream() {
      String output = "Continue? (y/n):" + System.lineSeparator();
      return new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public InputStream getErrorStream() {
      return InputStream.nullInputStream();
    }

    @Override
    public int waitFor() {
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) {
      return true;
    }

    @Override
    public int exitValue() {
      return 0;
    }

    @Override
    public void destroy() {}
  }
}

package com.healflow.engine.shell;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InteractiveShellRunnerSandboxTest {

  @Test
  void executesSimpleCommandThroughProcessStarter() {
    ProcessStarter starter = command -> new CompletedProcess(0, "healflow\n");
    InteractiveShellRunner runner = new InteractiveShellRunner(StandardCharsets.UTF_8, starter);

    CommandResult result =
        runner.run(new ShellCommand(List.of("ignored"), null, Duration.ofSeconds(1), Map.of(), List.of()));

    assertTrue(result.isSuccess());
    assertEquals("healflow", result.output().trim());
  }

  private static final class CompletedProcess extends Process {

    private final int exitCode;
    private final ByteArrayInputStream stdout;

    private CompletedProcess(int exitCode, String stdout) {
      this.exitCode = exitCode;
      this.stdout = new ByteArrayInputStream(stdout.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public OutputStream getOutputStream() {
      return OutputStream.nullOutputStream();
    }

    @Override
    public java.io.InputStream getInputStream() {
      return stdout;
    }

    @Override
    public java.io.InputStream getErrorStream() {
      return java.io.InputStream.nullInputStream();
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
}

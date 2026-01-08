package com.healflow.engine.sandbox;

import static org.junit.jupiter.api.Assertions.*;

import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.InteractionRule;
import com.healflow.engine.shell.InteractiveShellRunner;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellRunner;
import com.healflow.engine.testsupport.FakeShellRunner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class InteractiveRunnerTest {

  @Test
  void defaultConstructorExposesAutoApproveRules() {
    InteractiveRunner runner = new InteractiveRunner();
    assertFalse(runner.autoApproveRules().isEmpty());
  }

  @Test
  void injectsAutoApproveRulesWhenCommandHasNone() {
    FakeShellRunner shellRunner = new FakeShellRunner();
    shellRunner.enqueueResult(new CommandResult(0, "ok"));
    InteractiveRunner runner = new InteractiveRunner(shellRunner);

    runner.run(new ShellCommand(List.of("ignored"), null, Duration.ofSeconds(1), Map.of(), List.of()));

    List<InteractionRule> interactions = shellRunner.lastCommand().interactions();
    assertEquals(runner.autoApproveRules().size(), interactions.size());
    assertTrue(containsEquivalent(interactions, Pattern.compile("(?i)\\[\\s*y\\s*/\\s*n\\s*\\]"), "y"));
    assertTrue(containsEquivalent(interactions, Pattern.compile("(?i)\\[\\s*yes\\s*/\\s*no\\s*\\]"), "yes"));
  }

  @Test
  void mergesAutoApproveRulesWithExistingInteractions() {
    FakeShellRunner shellRunner = new FakeShellRunner();
    shellRunner.enqueueResult(new CommandResult(0, "ok"));
    InteractiveRunner runner = new InteractiveRunner(shellRunner);

    InteractionRule custom = new InteractionRule(Pattern.compile("prompt"), "custom", 1);
    runner.run(new ShellCommand(List.of("ignored"), null, Duration.ofSeconds(1), Map.of(), List.of(custom)));

    List<InteractionRule> interactions = shellRunner.lastCommand().interactions();
    assertEquals(1 + runner.autoApproveRules().size(), interactions.size());
    assertEquals("custom", interactions.get(0).response());
  }

  @Test
  void doesNotRebuildShellCommandWhenNoNewRulesAdded() {
    CommandResult expected = new CommandResult(0, "ok");
    ShellCommand command =
        new ShellCommand(
            List.of("ignored"),
            null,
            Duration.ofSeconds(1),
            Map.of(),
            List.of(
                new InteractionRule(Pattern.compile("(?i)\\(\\s*y\\s*/\\s*n\\s*\\)"), "y", 5),
                new InteractionRule(Pattern.compile("(?i)\\[\\s*y\\s*/\\s*n\\s*\\]"), "y", 5),
                new InteractionRule(Pattern.compile("(?i)\\(\\s*yes\\s*/\\s*no\\s*\\)"), "yes", 5),
                new InteractionRule(Pattern.compile("(?i)\\[\\s*yes\\s*/\\s*no\\s*\\]"), "yes", 5)));

    ShellRunner verifyingRunner =
        actual -> {
          assertSame(command, actual);
          return expected;
        };
    InteractiveRunner runner = new InteractiveRunner(verifyingRunner);

    assertSame(expected, runner.run(command));
  }

  @Test
  void autoApprovesCommonPromptsThroughInteractiveShellRunner() {
    PromptingProcess process =
        new PromptingProcess(
            "Allow? [y/N] ",
            "Continue? [yes/no] ",
            "ACK" + System.lineSeparator());
    InteractiveShellRunner shellRunner = new InteractiveShellRunner(StandardCharsets.UTF_8, command -> process);
    InteractiveRunner runner = new InteractiveRunner(shellRunner);

    CommandResult result =
        runner.run(new ShellCommand(List.of("ignored"), null, Duration.ofSeconds(2), Map.of(), List.of()));

    assertTrue(result.isSuccess());
    assertTrue(result.output().contains("ACK"));
    assertEquals(
        "y" + System.lineSeparator() + "yes" + System.lineSeparator(),
        process.stdinCapture.toString(StandardCharsets.UTF_8));
  }

  @Test
  void validatesArguments() {
    assertThrows(NullPointerException.class, () -> new InteractiveRunner(null));
    InteractiveRunner runner = new InteractiveRunner(command -> new CommandResult(0, "ok"));
    assertThrows(NullPointerException.class, () -> runner.run(null));
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

  private static final class PromptingProcess extends Process {

    private final ByteArrayOutputStream stdinCapture = new ByteArrayOutputStream();
    private final CountDownLatch firstReply = new CountDownLatch(1);
    private final CountDownLatch secondReply = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);
    private final InputStream stdout;

    private PromptingProcess(String firstPrompt, String secondPrompt, String finalOutput) {
      OutPipe pipe = new OutPipe();
      this.stdout = pipe.input;

      Thread script =
          new Thread(
              () -> {
                try {
                  pipe.write(firstPrompt);
                  awaitReply(firstReply);
                  pipe.write(secondPrompt);
                  awaitReply(secondReply);
                  pipe.write(finalOutput);
                } catch (RuntimeException e) {
                  pipe.close();
                  throw e;
                } finally {
                  pipe.close();
                  done.countDown();
                }
              },
              "prompting-process-script");
      script.setDaemon(true);
      script.start();
    }

    @Override
    public OutputStream getOutputStream() {
      return new OutputStream() {
        @Override
        public void write(int b) throws IOException {
          stdinCapture.write(b);
          if (b == '\n') {
            if (firstReply.getCount() != 0) {
              firstReply.countDown();
            } else {
              secondReply.countDown();
            }
          }
        }
      };
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
      done.await();
      return 0;
    }

    @Override
    public boolean waitFor(long timeout, TimeUnit unit) throws InterruptedException {
      return done.await(timeout, unit);
    }

    @Override
    public int exitValue() {
      if (done.getCount() != 0) {
        throw new IllegalThreadStateException();
      }
      return 0;
    }

    @Override
    public void destroy() {
    }

    private static void awaitReply(CountDownLatch latch) {
      try {
        if (!latch.await(2, TimeUnit.SECONDS)) {
          throw new IllegalStateException("Timed out waiting for reply");
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted waiting for reply", e);
      }
    }

    private static final class OutPipe {
      private final java.io.PipedInputStream input;
      private final java.io.PipedOutputStream output;

      private OutPipe() {
        try {
          this.input = new java.io.PipedInputStream();
          this.output = new java.io.PipedOutputStream(input);
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }

      private void write(String value) {
        try {
          output.write(value.getBytes(StandardCharsets.UTF_8));
          output.flush();
        } catch (IOException e) {
          throw new IllegalStateException(e);
        }
      }

      private void close() {
        try {
          output.close();
        } catch (IOException ignored) {
        }
      }
    }
  }
}

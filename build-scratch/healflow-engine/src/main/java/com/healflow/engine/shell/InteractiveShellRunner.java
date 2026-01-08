package com.healflow.engine.shell;

import com.healflow.common.validation.Arguments;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

public final class InteractiveShellRunner implements ShellRunner {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

  private final Charset charset;
  private final ProcessStarter processStarter;

  public InteractiveShellRunner() {
    this(StandardCharsets.UTF_8, InteractiveShellRunner::startProcess);
  }

  public InteractiveShellRunner(Charset charset) {
    this(charset, InteractiveShellRunner::startProcess);
  }

  public InteractiveShellRunner(Charset charset, ProcessStarter processStarter) {
    this.charset = Arguments.requireNonNull(charset, "charset");
    this.processStarter = Arguments.requireNonNull(processStarter, "processStarter");
  }

  @Override
  public CommandResult run(ShellCommand command) {
    Arguments.requireNonNull(command, "command");

    Duration timeout = command.timeout() == null ? DEFAULT_TIMEOUT : command.timeout();
    Duration idleTimeout = command.idleTimeout();
    if (timeout.isZero()) {
      throw new IllegalArgumentException("timeout must not be zero");
    }

    Process process;
    try {
      process = processStarter.start(command);
    } catch (IOException e) {
      throw new ShellExecutionException(command, e);
    }

    StringBuilder output = new StringBuilder();
    Object outputLock = new Object();

    int interactionCount = command.interactions().size();
    int[] searchFrom = new int[interactionCount];
    int[] replies = new int[interactionCount];

    Writer stdinWriter = new OutputStreamWriter(process.getOutputStream(), charset);

    if (command.interactions().isEmpty()) {
      try {
        stdinWriter.close();
      } catch (IOException ignored) {
      }
    }

    AtomicReference<RuntimeException> readerFailure = new AtomicReference<>();
    AtomicReference<Long> lastOutputTime = new AtomicReference<>(System.currentTimeMillis());
    Thread readerThread =
        new Thread(
            () ->
                pumpOutput(
                    command,
                    process,
                    stdinWriter,
                    output,
                    outputLock,
                    searchFrom,
                    replies,
                    readerFailure,
                    lastOutputTime),
            "shell-output-pump");
    readerThread.setDaemon(true);
    readerThread.start();

    boolean finished;
    try {
      if (idleTimeout != null) {
        finished = waitWithIdleTimeout(process, timeout, idleTimeout, lastOutputTime);
      } else {
        finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      destroy(process);
      throw new ShellExecutionException(command, e);
    }

    if (!finished) {
      destroy(process);
      try {
        readerThread.join(TimeUnit.SECONDS.toMillis(2));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      String outputSoFar;
      synchronized (outputLock) {
        outputSoFar = output.toString();
      }
      throw new ShellTimeoutException(command, outputSoFar);
    }

    try {
      readerThread.join(TimeUnit.SECONDS.toMillis(2));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ShellExecutionException(command, e);
    }

    RuntimeException failure = readerFailure.get();
    if (failure != null) {
      throw failure;
    }

    String finalOutput;
    synchronized (outputLock) {
      finalOutput = output.toString();
    }
    return new CommandResult(process.exitValue(), finalOutput);
  }

  private boolean waitWithIdleTimeout(
      Process process, Duration totalTimeout, Duration idleTimeout, AtomicReference<Long> lastOutputTime)
      throws InterruptedException {
    long startTime = System.currentTimeMillis();
    long totalTimeoutMs = totalTimeout.toMillis();
    long idleTimeoutMs = idleTimeout.toMillis();
    long checkInterval = Math.min(1000, idleTimeoutMs / 2);

    while (true) {
      if (process.waitFor(checkInterval, TimeUnit.MILLISECONDS)) {
        return true;
      }

      long now = System.currentTimeMillis();
      long elapsed = now - startTime;
      long idleTime = now - lastOutputTime.get();

      if (elapsed >= totalTimeoutMs) {
        return false;
      }

      if (idleTime >= idleTimeoutMs) {
        return false;
      }
    }
  }

  private static void configureWorkingDirectory(ProcessBuilder processBuilder, Path workingDirectory) {
    if (workingDirectory != null) {
      processBuilder.directory(workingDirectory.toFile());
    }
  }

  private static void configureEnvironment(ProcessBuilder processBuilder, Map<String, String> environment) {
    if (!environment.isEmpty()) {
      Map<String, String> target = processBuilder.environment();
      target.putAll(environment);
    }
  }

  private static Process startProcess(ShellCommand command) throws IOException {
    ProcessBuilder processBuilder = new ProcessBuilder(command.argv());
    configureWorkingDirectory(processBuilder, command.workingDirectory());
    configureEnvironment(processBuilder, command.environment());
    processBuilder.redirectErrorStream(true);
    return processBuilder.start();
  }

  private void pumpOutput(
      ShellCommand command,
      Process process,
      Writer stdinWriter,
      StringBuilder output,
      Object outputLock,
      int[] searchFrom,
      int[] replies,
      AtomicReference<RuntimeException> readerFailure,
      AtomicReference<Long> lastOutputTime) {
    try (InputStream inputStream = process.getInputStream()) {
      byte[] buffer = new byte[4096];
      int bytesRead;
      while ((bytesRead = inputStream.read(buffer)) != -1) {
        lastOutputTime.set(System.currentTimeMillis());
        String chunk = new String(buffer, 0, bytesRead, charset);
        synchronized (outputLock) {
          output.append(chunk);
          applyInteractions(command.interactions(), stdinWriter, output, searchFrom, replies);
        }
      }
    } catch (IOException e) {
      readerFailure.compareAndSet(null, new ShellExecutionException(command, e));
    }
  }

  private static void applyInteractions(
      List<InteractionRule> interactions,
      Writer stdinWriter,
      StringBuilder output,
      int[] searchFrom,
      int[] replies) {
    for (int i = 0; i < interactions.size(); i++) {
      InteractionRule rule = interactions.get(i);
      if (replies[i] >= rule.maxReplies()) {
        continue;
      }
      Matcher matcher = rule.pattern().matcher(output);
      if (!matcher.find(searchFrom[i])) {
        continue;
      }
      searchFrom[i] = matcher.end();
      replies[i]++;
      try {
        stdinWriter.write(rule.response());
        stdinWriter.write(System.lineSeparator());
        stdinWriter.flush();
      } catch (IOException ignored) {
        return;
      }
    }
  }

  private static void destroy(Process process) {
    process.destroy();
    try {
      if (!process.waitFor(1, TimeUnit.SECONDS)) {
        process.destroyForcibly();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      process.destroyForcibly();
    }
  }
}

package com.healflow.engine.sandbox;

import com.healflow.common.validation.Arguments;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.InteractionRule;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellExecutionException;
import com.healflow.engine.shell.ShellRunner;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class DockerSandboxManager {

  private static final Pattern SAFE_CONTAINER_NAME = Pattern.compile("[a-zA-Z0-9_.-]+");
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

  private final ShellRunner shellRunner;
  private final String dockerExecutable;

  public DockerSandboxManager(ShellRunner shellRunner) {
    this(shellRunner, "docker");
  }

  public DockerSandboxManager(ShellRunner shellRunner, String dockerExecutable) {
    this.shellRunner = Arguments.requireNonNull(shellRunner, "shellRunner");
    this.dockerExecutable = Arguments.requireNonBlank(dockerExecutable, "dockerExecutable");
  }

  public String startDetached(
      String containerName,
      Path hostWorkspace,
      String containerWorkspace,
      String image,
      Map<String, String> environment) {
    String safeName = requireSafeContainerName(containerName);
    Arguments.requireNonNull(hostWorkspace, "hostWorkspace");
    Arguments.requireNonBlank(containerWorkspace, "containerWorkspace");
    Arguments.requireNonBlank(image, "image");
    Arguments.requireNonNull(environment, "environment");

    List<String> argv = new ArrayList<>();
    argv.add(dockerExecutable);
    argv.addAll(List.of("run", "-d", "--name", safeName));
    argv.addAll(List.of("-v", hostWorkspace.toString() + ":" + containerWorkspace));
    argv.addAll(List.of("-w", containerWorkspace));

    for (Map.Entry<String, String> entry : environment.entrySet()) {
      String key = Arguments.requireNonBlank(entry.getKey(), "environment key");
      String value = Arguments.requireNonNull(entry.getValue(), "environment value");
      argv.add("-e");
      argv.add(key + "=" + value);
    }

    argv.add(image);
    argv.addAll(List.of("tail", "-f", "/dev/null"));

    CommandResult result = run(new ShellCommand(argv, null, DEFAULT_TIMEOUT, Map.of(), List.of()));
    String containerId = result.output().trim();
    if (containerId.isEmpty()) {
      SandboxException failure = new SandboxException("Docker did not return container ID", result.output());
      try {
        removeForce(safeName);
      } catch (RuntimeException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
    return containerId;
  }

  public CommandResult exec(
      String containerName, List<String> argv, Duration timeout, List<InteractionRule> interactions) {
    String safeName = requireSafeContainerName(containerName);
    Arguments.requireNonNull(argv, "argv");
    if (argv.isEmpty()) {
      throw new IllegalArgumentException("argv must be non-empty");
    }
    Arguments.requireNonNull(interactions, "interactions");

    List<String> fullArgv = new ArrayList<>();
    fullArgv.add(dockerExecutable);
    fullArgv.addAll(List.of("exec", "-i", safeName));
    fullArgv.addAll(argv);

    Duration effectiveTimeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    return run(new ShellCommand(fullArgv, null, effectiveTimeout, Map.of(), interactions));
  }

  public void removeForce(String containerName) {
    String safeName = requireSafeContainerName(containerName);
    run(new ShellCommand(List.of(dockerExecutable, "rm", "-f", safeName), null, DEFAULT_TIMEOUT, Map.of(), List.of()));
  }

  public CommandResult executeInSandbox(
      String containerName,
      Path hostWorkspace,
      String containerWorkspace,
      String image,
      List<String> argv) {
    return executeInSandbox(
        containerName, hostWorkspace, containerWorkspace, image, Map.of(), argv, null, List.of());
  }

  public CommandResult executeInSandbox(
      String containerName,
      Path hostWorkspace,
      String containerWorkspace,
      String image,
      Map<String, String> environment,
      List<String> argv,
      Duration timeout,
      List<InteractionRule> interactions) {
    Arguments.requireNonNull(hostWorkspace, "hostWorkspace");
    Arguments.requireNonBlank(containerWorkspace, "containerWorkspace");
    Arguments.requireNonBlank(image, "image");
    Arguments.requireNonNull(environment, "environment");
    Arguments.requireNonNull(argv, "argv");
    if (argv.isEmpty()) {
      throw new IllegalArgumentException("argv must be non-empty");
    }
    if (argv.stream().anyMatch(a -> a == null || a.isBlank())) {
      throw new IllegalArgumentException("argv must be non-empty and contain no blank arguments");
    }
    if (timeout != null && timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must not be negative");
    }
    Arguments.requireNonNull(interactions, "interactions");

    boolean containerStarted = false;
    RuntimeException failure = null;
    try {
      startDetached(containerName, hostWorkspace, containerWorkspace, image, environment);
      containerStarted = true;
      return exec(containerName, argv, timeout, interactions);
    } catch (RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      if (containerStarted) {
        try {
          removeForce(containerName);
        } catch (RuntimeException cleanupFailure) {
          if (failure != null) {
            failure.addSuppressed(cleanupFailure);
          } else {
            throw cleanupFailure;
          }
        }
      }
    }
  }

  private CommandResult run(ShellCommand command) {
    CommandResult result;
    try {
      result = shellRunner.run(command);
    } catch (ShellExecutionException e) {
      throw new SandboxException("Docker command failed to execute", String.join(" ", command.argv()), e);
    }
    if (!result.isSuccess()) {
      throw new SandboxException("Docker command failed", formatFailure(command.argv(), result));
    }
    return result;
  }

  private static String requireSafeContainerName(String containerName) {
    String name = Arguments.requireNonBlank(containerName, "containerName");
    if (!SAFE_CONTAINER_NAME.matcher(name).matches()) {
      throw new IllegalArgumentException("containerName contains illegal characters: " + containerName);
    }
    return name;
  }

  private static String formatFailure(List<String> argv, CommandResult result) {
    return "command=" + String.join(" ", argv) + System.lineSeparator() + result.output();
  }
}

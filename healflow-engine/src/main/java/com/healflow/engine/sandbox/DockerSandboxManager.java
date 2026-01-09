package com.healflow.engine.sandbox;

import com.healflow.common.validation.Arguments;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.InteractionRule;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellExecutionException;
import com.healflow.engine.shell.ShellRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DockerSandboxManager {

  private static final Logger log = LoggerFactory.getLogger(DockerSandboxManager.class);
  private static final Pattern SAFE_CONTAINER_NAME = Pattern.compile("[a-zA-Z0-9_.-]+");
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

  private final ShellRunner shellRunner;
  private final InteractiveRunner interactiveRunner;
  private final String dockerExecutable;

  public DockerSandboxManager(ShellRunner shellRunner) {
    this(shellRunner, "docker");
  }

  public DockerSandboxManager(ShellRunner shellRunner, String dockerExecutable) {
    this.shellRunner = Arguments.requireNonNull(shellRunner, "shellRunner");
    this.interactiveRunner = new InteractiveRunner(shellRunner);
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

  public CommandResult executeInteractiveRunInSandbox(
      String containerName,
      Path hostWorkspace,
      String containerWorkspace,
      String image,
      Map<String, String> environment,
      List<String> argv,
      Duration timeout,
      List<InteractionRule> interactions) {
    String safeName = requireSafeContainerName(containerName);
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

    Duration effectiveTimeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
    Duration idleTimeout = Duration.ofMinutes(30);

    // 检查容器是否已存在
    boolean containerExists = checkContainerExists(safeName);
    log.info("Container {} exists: {}", safeName, containerExists);

    if (containerExists) {
      // 容器已存在，使用 docker exec
      return executeInExistingContainer(safeName, containerWorkspace, argv, effectiveTimeout, idleTimeout, interactions);
    }

    // 容器不存在，创建新容器（不使用 --rm，手动管理生命周期）
    List<String> dockerArgv = new ArrayList<>();
    dockerArgv.add(dockerExecutable);
    dockerArgv.add("run");

    // Only use -i flag if there are user-provided interactions
    // For non-interactive commands (empty interactions), omit -i to avoid stdin deadlock
    if (!interactions.isEmpty()) {
      dockerArgv.add("-i");
    }

    // 不使用 --rm，让容器保持运行以便复用 session 数据
    dockerArgv.addAll(List.of("--name", safeName));
    dockerArgv.addAll(List.of("-v", hostWorkspace.toString() + ":" + containerWorkspace));
    dockerArgv.addAll(List.of("-w", containerWorkspace));

    for (Map.Entry<String, String> entry : environment.entrySet()) {
      String key = Arguments.requireNonBlank(entry.getKey(), "environment key");
      String value = Arguments.requireNonNull(entry.getValue(), "environment value");
      dockerArgv.add("-e");
      dockerArgv.add(key + "=" + value);
    }

    dockerArgv.add(image);
    dockerArgv.addAll(argv);

    // 不再自动删除容器，让容器保持运行以便复用
    if (interactions.isEmpty()) {
      return run(new ShellCommand(dockerArgv, null, effectiveTimeout, Map.of(), List.of(), idleTimeout));
    } else {
      return runInteractive(new ShellCommand(dockerArgv, null, effectiveTimeout, Map.of(), interactions, idleTimeout));
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

  private CommandResult runInteractive(ShellCommand command) {
    CommandResult result;
    try {
      result = interactiveRunner.run(command);
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

  private boolean checkContainerExists(String containerName) {
    try {
      List<String> cmd = List.of(dockerExecutable, "inspect", "-f", "{{.State.Running}}", containerName);
      CommandResult result = shellRunner.run(new ShellCommand(cmd, null, Duration.ofSeconds(10), Map.of(), List.of()));
      return result.exitCode() == 0;
    } catch (Exception e) {
      return false;
    }
  }

  private CommandResult executeInExistingContainer(
      String containerName,
      String containerWorkspace,
      List<String> argv,
      Duration timeout,
      Duration idleTimeout,
      List<InteractionRule> interactions) {

    log.info("Executing in existing container: {}", containerName);

    // 检查容器状态
    List<String> inspectCmd = List.of(dockerExecutable, "inspect", "-f", "{{.State.Running}}", containerName);
    ShellCommand cmd = new ShellCommand(inspectCmd, null, Duration.ofSeconds(10), Map.of(), List.of());
    CommandResult result = shellRunner.run(cmd);
    boolean isRunning = result.output().trim().equals("true");

    log.info("Container {} running status: {}", containerName, isRunning);

    if (!isRunning) {
      log.warn("Container {} is not running, needs recreation", containerName);
      throw new RuntimeException("Container exists but is not running, needs recreation: " + containerName);
    }

    List<String> dockerArgv = new ArrayList<>();
    dockerArgv.add(dockerExecutable);
    dockerArgv.add("exec");

    if (!interactions.isEmpty()) {
      dockerArgv.add("-i");
    }

    dockerArgv.add("-w");
    dockerArgv.add(containerWorkspace);
    dockerArgv.add(containerName);
    dockerArgv.addAll(argv);

    log.info("Executing command in container: {}", String.join(" ", dockerArgv));
    log.info("Timeout: {}, Idle timeout: {}", timeout, idleTimeout);

    if (interactions.isEmpty()) {
      return run(new ShellCommand(dockerArgv, null, timeout, Map.of(), List.of(), idleTimeout));
    } else {
      return runInteractive(new ShellCommand(dockerArgv, null, timeout, Map.of(), interactions, idleTimeout));
    }
  }
}

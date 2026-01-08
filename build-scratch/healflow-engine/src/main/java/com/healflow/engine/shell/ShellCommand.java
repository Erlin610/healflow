package com.healflow.engine.shell;

import com.healflow.common.validation.Arguments;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public record ShellCommand(
    List<String> argv,
    Path workingDirectory,
    Duration timeout,
    Map<String, String> environment,
    List<InteractionRule> interactions,
    Duration idleTimeout) {

  public ShellCommand {
    Arguments.requireNonNull(argv, "argv");
    if (argv.isEmpty() || argv.stream().anyMatch(a -> a == null || a.isBlank())) {
      throw new IllegalArgumentException("argv must be non-empty and contain no blank arguments");
    }
    Arguments.requireNonNull(environment, "environment");
    if (environment.keySet().stream().anyMatch(k -> k == null || k.isBlank())) {
      throw new IllegalArgumentException("environment must not contain blank keys");
    }
    Arguments.requireNonNull(interactions, "interactions");
    if (timeout != null && timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must not be negative");
    }
    if (idleTimeout != null && idleTimeout.isNegative()) {
      throw new IllegalArgumentException("idleTimeout must not be negative");
    }
    argv = List.copyOf(argv);
    environment = Map.copyOf(environment);
    interactions = List.copyOf(interactions);
  }

  public ShellCommand(
      List<String> argv,
      Path workingDirectory,
      Duration timeout,
      Map<String, String> environment,
      List<InteractionRule> interactions) {
    this(argv, workingDirectory, timeout, environment, interactions, null);
  }

  public static ShellCommand of(List<String> argv) {
    return new ShellCommand(argv, null, null, Map.of(), List.of());
  }
}


package com.healflow.engine.testsupport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.InteractiveShellRunner;
import com.healflow.engine.shell.ShellCommand;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class GitTestSupport {

  private GitTestSupport() {}

  public static void git(Path workingDirectory, String... args) {
    InteractiveShellRunner runner = new InteractiveShellRunner();
    List<String> argv = new java.util.ArrayList<>(args.length + 1);
    argv.add("git");
    argv.addAll(List.of(args));

    CommandResult result =
        runner.run(new ShellCommand(argv, workingDirectory, Duration.ofMinutes(1), Map.of(), List.of()));
    assertEquals(0, result.exitCode(), () -> "git " + String.join(" ", args) + System.lineSeparator() + result.output());
  }

  public static String gitOutput(Path workingDirectory, String... args) {
    InteractiveShellRunner runner = new InteractiveShellRunner();
    List<String> argv = new java.util.ArrayList<>(args.length + 1);
    argv.add("git");
    argv.addAll(List.of(args));

    CommandResult result =
        runner.run(new ShellCommand(argv, workingDirectory, Duration.ofMinutes(1), Map.of(), List.of()));
    assertEquals(0, result.exitCode(), () -> "git " + String.join(" ", args) + System.lineSeparator() + result.output());
    return result.output();
  }
}


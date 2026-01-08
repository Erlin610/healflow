package com.healflow.engine.testsupport;

import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellRunner;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

public final class FakeShellRunner implements ShellRunner {

  private final Deque<CommandResult> results = new ArrayDeque<>();
  private ShellCommand lastCommand;

  public void enqueueResult(CommandResult result) {
    results.add(Objects.requireNonNull(result, "result"));
  }

  public ShellCommand lastCommand() {
    return lastCommand;
  }

  @Override
  public CommandResult run(ShellCommand command) {
    this.lastCommand = command;
    if (results.isEmpty()) {
      throw new IllegalStateException("No queued result for command: " + command.argv());
    }
    return results.removeFirst();
  }
}


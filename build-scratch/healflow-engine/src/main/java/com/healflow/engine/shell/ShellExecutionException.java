package com.healflow.engine.shell;

import com.healflow.common.validation.Arguments;

public class ShellExecutionException extends RuntimeException {

  private final ShellCommand command;

  public ShellExecutionException(ShellCommand command, String message) {
    super(Arguments.requireNonBlank(message, "message"));
    this.command = Arguments.requireNonNull(command, "command");
  }

  public ShellExecutionException(ShellCommand command, Throwable cause) {
    super(Arguments.requireNonNull(cause, "cause"));
    this.command = Arguments.requireNonNull(command, "command");
  }

  public ShellCommand command() {
    return command;
  }
}


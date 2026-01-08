package com.healflow.engine.shell;

import com.healflow.common.validation.Arguments;

public record CommandResult(int exitCode, String output) {

  public CommandResult {
    Arguments.requireNonNull(output, "output");
  }

  public boolean isSuccess() {
    return exitCode == 0;
  }
}


package com.healflow.engine.shell;

import com.healflow.common.validation.Arguments;

public final class ShellTimeoutException extends ShellExecutionException {

  private final String outputSoFar;

  public ShellTimeoutException(ShellCommand command, String outputSoFar) {
    super(command, "Command timed out");
    this.outputSoFar = Arguments.requireNonNull(outputSoFar, "outputSoFar");
  }

  public String outputSoFar() {
    return outputSoFar;
  }
}


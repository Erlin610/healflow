package com.healflow.engine.git;

import com.healflow.common.validation.Arguments;

public final class GitException extends RuntimeException {

  private final String details;

  public GitException(String message, String details) {
    super(Arguments.requireNonBlank(message, "message"));
    this.details = Arguments.requireNonNull(details, "details");
  }

  public GitException(String message, String details, Throwable cause) {
    super(Arguments.requireNonBlank(message, "message"), Arguments.requireNonNull(cause, "cause"));
    this.details = Arguments.requireNonNull(details, "details");
  }

  public String details() {
    return details;
  }
}


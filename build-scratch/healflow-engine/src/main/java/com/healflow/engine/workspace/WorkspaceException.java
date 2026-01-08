package com.healflow.engine.workspace;

import com.healflow.common.validation.Arguments;

public final class WorkspaceException extends RuntimeException {

  private final String details;

  public WorkspaceException(String message, String details) {
    super(Arguments.requireNonBlank(message, "message"));
    this.details = Arguments.requireNonNull(details, "details");
  }

  public WorkspaceException(String message, String details, Throwable cause) {
    super(Arguments.requireNonBlank(message, "message"), Arguments.requireNonNull(cause, "cause"));
    this.details = Arguments.requireNonNull(details, "details");
  }

  public String details() {
    return details;
  }
}


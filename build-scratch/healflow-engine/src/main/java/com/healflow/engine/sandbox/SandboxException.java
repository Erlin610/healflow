package com.healflow.engine.sandbox;

import com.healflow.common.validation.Arguments;

public final class SandboxException extends RuntimeException {

  private final String details;

  public SandboxException(String message, String details) {
    super(Arguments.requireNonBlank(message, "message"));
    this.details = Arguments.requireNonNull(details, "details");
  }

  public SandboxException(String message, String details, Throwable cause) {
    super(Arguments.requireNonBlank(message, "message"), Arguments.requireNonNull(cause, "cause"));
    this.details = Arguments.requireNonNull(details, "details");
  }

  public String details() {
    return details;
  }

  @Override
  public String getMessage() {
    return super.getMessage() + "\n" + details;
  }
}

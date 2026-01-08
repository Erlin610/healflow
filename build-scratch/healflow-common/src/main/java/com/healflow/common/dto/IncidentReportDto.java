package com.healflow.common.dto;

import com.healflow.common.validation.Arguments;
import jakarta.validation.constraints.NotBlank;

public record IncidentReportDto(
    @NotBlank String appName,
    @NotBlank String stackTrace,
    @NotBlank String branch) {

  public IncidentReportDto {
    appName = Arguments.requireNonBlank(appName, "appName");
    stackTrace = Arguments.requireNonBlank(stackTrace, "stackTrace");
    branch = Arguments.requireNonBlank(branch, "branch");
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String appName;
    private String stackTrace;
    private String branch;

    private Builder() {}

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder stackTrace(String stackTrace) {
      this.stackTrace = stackTrace;
      return this;
    }

    public Builder branch(String branch) {
      this.branch = branch;
      return this;
    }

    public IncidentReportDto build() {
      return new IncidentReportDto(appName, stackTrace, branch);
    }
  }
}

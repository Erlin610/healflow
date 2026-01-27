package com.healflow.common.dto;

import com.healflow.common.validation.Arguments;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

public class IncidentReportDto {

  @NotBlank private String appName;
  @NotBlank private String stackTrace;
  @NotBlank private String branch;

  public IncidentReportDto() {}

  public IncidentReportDto(String appName, String stackTrace, String branch) {
    this.appName = Arguments.requireNonBlank(appName, "appName");
    this.stackTrace = Arguments.requireNonBlank(stackTrace, "stackTrace");
    this.branch = Arguments.requireNonBlank(branch, "branch");
  }

  // Record-style accessors for source compatibility with previous record usage.
  public String appName() {
    return appName;
  }

  public String stackTrace() {
    return stackTrace;
  }

  public String branch() {
    return branch;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = Arguments.requireNonBlank(appName, "appName");
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = Arguments.requireNonBlank(stackTrace, "stackTrace");
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = Arguments.requireNonBlank(branch, "branch");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IncidentReportDto)) {
      return false;
    }
    IncidentReportDto that = (IncidentReportDto) o;
    return Objects.equals(appName, that.appName)
        && Objects.equals(stackTrace, that.stackTrace)
        && Objects.equals(branch, that.branch);
  }

  @Override
  public int hashCode() {
    return Objects.hash(appName, stackTrace, branch);
  }

  @Override
  public String toString() {
    return "IncidentReportDto[" + "appName=" + appName + ", stackTrace=" + stackTrace + ", branch=" + branch + ']';
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

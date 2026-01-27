package com.healflow.common.dto;

import com.healflow.common.enums.AgentStatus;
import com.healflow.common.validation.Arguments;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class PatchProposalDto {

  @NotBlank private String appName;
  @NotBlank private String branch;
  @NotBlank private String patch;
  @NotNull private AgentStatus agentStatus;

  public PatchProposalDto() {}

  public PatchProposalDto(String appName, String branch, String patch, AgentStatus agentStatus) {
    this.appName = Arguments.requireNonBlank(appName, "appName");
    this.branch = Arguments.requireNonBlank(branch, "branch");
    this.patch = Arguments.requireNonBlank(patch, "patch");
    this.agentStatus = Arguments.requireNonNull(agentStatus, "agentStatus");
  }

  // Record-style accessors for source compatibility with previous record usage.
  public String appName() {
    return appName;
  }

  public String branch() {
    return branch;
  }

  public String patch() {
    return patch;
  }

  public AgentStatus agentStatus() {
    return agentStatus;
  }

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = Arguments.requireNonBlank(appName, "appName");
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = Arguments.requireNonBlank(branch, "branch");
  }

  public String getPatch() {
    return patch;
  }

  public void setPatch(String patch) {
    this.patch = Arguments.requireNonBlank(patch, "patch");
  }

  public AgentStatus getAgentStatus() {
    return agentStatus;
  }

  public void setAgentStatus(AgentStatus agentStatus) {
    this.agentStatus = Arguments.requireNonNull(agentStatus, "agentStatus");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PatchProposalDto)) {
      return false;
    }
    PatchProposalDto that = (PatchProposalDto) o;
    return Objects.equals(appName, that.appName)
        && Objects.equals(branch, that.branch)
        && Objects.equals(patch, that.patch)
        && agentStatus == that.agentStatus;
  }

  @Override
  public int hashCode() {
    return Objects.hash(appName, branch, patch, agentStatus);
  }

  @Override
  public String toString() {
    return "PatchProposalDto["
        + "appName="
        + appName
        + ", branch="
        + branch
        + ", patch="
        + patch
        + ", agentStatus="
        + agentStatus
        + ']';
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {

    private String appName;
    private String branch;
    private String patch;
    private AgentStatus agentStatus;

    private Builder() {}

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder branch(String branch) {
      this.branch = branch;
      return this;
    }

    public Builder patch(String patch) {
      this.patch = patch;
      return this;
    }

    public Builder agentStatus(AgentStatus agentStatus) {
      this.agentStatus = agentStatus;
      return this;
    }

    public PatchProposalDto build() {
      return new PatchProposalDto(appName, branch, patch, agentStatus);
    }
  }
}

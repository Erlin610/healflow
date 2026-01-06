package com.healflow.common.dto;

import com.healflow.common.enums.AgentStatus;
import com.healflow.common.validation.Arguments;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PatchProposalDto(
    @NotBlank String appName,
    @NotBlank String branch,
    @NotBlank String patch,
    @NotNull AgentStatus agentStatus) {

  public PatchProposalDto {
    appName = Arguments.requireNonBlank(appName, "appName");
    branch = Arguments.requireNonBlank(branch, "branch");
    patch = Arguments.requireNonBlank(patch, "patch");
    agentStatus = Arguments.requireNonNull(agentStatus, "agentStatus");
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

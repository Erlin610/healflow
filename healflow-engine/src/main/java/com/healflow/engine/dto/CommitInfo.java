package com.healflow.engine.dto;

import com.healflow.common.validation.Arguments;
import java.util.List;

public record CommitInfo(
    String commitId,
    String commitMessage,
    List<String> changedFiles,
    List<FileDiffStat> gitDiff) {

  public CommitInfo {
    commitId = Arguments.requireNonBlank(commitId, "commitId");
    commitMessage = Arguments.requireNonBlank(commitMessage, "commitMessage");
    changedFiles = List.copyOf(Arguments.requireNonNull(changedFiles, "changedFiles"));
    gitDiff = List.copyOf(Arguments.requireNonNull(gitDiff, "gitDiff"));
  }
}

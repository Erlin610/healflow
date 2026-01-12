package com.healflow.engine.dto;

import com.healflow.common.validation.Arguments;

public record FileDiffStat(String fileName, int addedLines, int deletedLines) {

  public FileDiffStat {
    fileName = Arguments.requireNonBlank(fileName, "fileName");
  }
}

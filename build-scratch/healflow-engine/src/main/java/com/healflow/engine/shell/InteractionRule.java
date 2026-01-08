package com.healflow.engine.shell;

import com.healflow.common.validation.Arguments;
import java.util.regex.Pattern;

public record InteractionRule(Pattern pattern, String response, int maxReplies) {

  public InteractionRule {
    Arguments.requireNonNull(pattern, "pattern");
    Arguments.requireNonBlank(response, "response");
    if (maxReplies < 1) {
      throw new IllegalArgumentException("maxReplies must be >= 1");
    }
  }
}


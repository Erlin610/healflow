package com.healflow.engine.sandbox;

import com.healflow.common.validation.Arguments;
import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.InteractionRule;
import com.healflow.engine.shell.InteractiveShellRunner;
import com.healflow.engine.shell.ShellCommand;
import com.healflow.engine.shell.ShellRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class InteractiveRunner {

  private static final int AUTO_APPROVE_MAX_REPLIES = 5;
  private static final List<InteractionRule> AUTO_APPROVE_RULES =
      List.of(
          new InteractionRule(Pattern.compile("(?i)\\(\\s*y\\s*/\\s*n\\s*\\)"), "y", AUTO_APPROVE_MAX_REPLIES),
          new InteractionRule(Pattern.compile("(?i)\\[\\s*y\\s*/\\s*n\\s*\\]"), "y", AUTO_APPROVE_MAX_REPLIES),
          new InteractionRule(Pattern.compile("(?i)\\(\\s*yes\\s*/\\s*no\\s*\\)"), "yes", AUTO_APPROVE_MAX_REPLIES),
          new InteractionRule(Pattern.compile("(?i)\\[\\s*yes\\s*/\\s*no\\s*\\]"), "yes", AUTO_APPROVE_MAX_REPLIES));

  private final ShellRunner shellRunner;

  public InteractiveRunner() {
    this(new InteractiveShellRunner());
  }

  public InteractiveRunner(ShellRunner shellRunner) {
    this.shellRunner = Arguments.requireNonNull(shellRunner, "shellRunner");
  }

  public List<InteractionRule> autoApproveRules() {
    return AUTO_APPROVE_RULES;
  }

  public CommandResult run(ShellCommand command) {
    Arguments.requireNonNull(command, "command");
    List<InteractionRule> effectiveInteractions = mergeInteractions(command.interactions());
    if (effectiveInteractions == command.interactions()) {
      return shellRunner.run(command);
    }
    return shellRunner.run(
        new ShellCommand(
            command.argv(),
            command.workingDirectory(),
            command.timeout(),
            command.environment(),
            effectiveInteractions));
  }

  private static List<InteractionRule> mergeInteractions(List<InteractionRule> interactions) {
    if (interactions.isEmpty()) {
      return AUTO_APPROVE_RULES;
    }

    List<InteractionRule> merged = new ArrayList<>(interactions);
    int originalSize = merged.size();
    for (InteractionRule autoRule : AUTO_APPROVE_RULES) {
      if (!containsEquivalent(interactions, autoRule)) {
        merged.add(autoRule);
      }
    }
    if (merged.size() == originalSize) {
      return interactions;
    }
    return List.copyOf(merged);
  }

  private static boolean containsEquivalent(List<InteractionRule> interactions, InteractionRule candidate) {
    Pattern candidatePattern = candidate.pattern();
    int candidateFlags = candidatePattern.flags();
    String candidateExpression = candidatePattern.pattern();
    for (InteractionRule existing : interactions) {
      if (!existing.response().equals(candidate.response())) {
        continue;
      }
      Pattern existingPattern = existing.pattern();
      if (existingPattern.flags() == candidateFlags && existingPattern.pattern().equals(candidateExpression)) {
        return true;
      }
    }
    return false;
  }
}


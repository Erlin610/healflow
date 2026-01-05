package com.healflow.engine;

import com.healflow.common.validation.Arguments;
import java.util.Locale;

public final class SimpleHealflowEngine implements HealflowEngine {

  private final String highSeverityTokenLower;

  public SimpleHealflowEngine() {
    this("panic");
  }

  public SimpleHealflowEngine(String highSeverityToken) {
    this.highSeverityTokenLower =
        Arguments.requireNonBlank(highSeverityToken, "highSeverityToken").toLowerCase(Locale.ROOT);
  }

  @Override
  public HealingResult analyze(String incidentReport) {
    String report = Arguments.requireNonBlank(incidentReport, "incidentReport");
    String reportLower = report.toLowerCase(Locale.ROOT);

    Severity severity = reportLower.contains(highSeverityTokenLower) ? Severity.HIGH : Severity.LOW;
    String message = (severity == Severity.HIGH) ? "triage_required" : "no_action";
    return new HealingResult(severity, message);
  }
}


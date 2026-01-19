package com.healflow.platform.dto;

import com.healflow.common.enums.IncidentStatus;
import java.time.Instant;

public record WebhookPayload(
    String appId,
    String incidentId,
    IncidentStatus status,
    String errorType,
    String errorMessage,
    Instant occurredAt,
    AnalysisInfo analysis) {

  public record AnalysisInfo(
      String sessionId,
      String rootCause,
      String summary,
      String severity,
      String detailUrl) {}

  public boolean isRegression() {
    return status == IncidentStatus.REGRESSION;
  }

  public boolean hasAnalysis() {
    return analysis != null;
  }
}

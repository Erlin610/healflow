package com.healflow.platform.dto;

import com.healflow.common.enums.IncidentStatus;
import java.time.Instant;

public record WebhookPayload(
    String appId,
    String incidentId,
    IncidentStatus status,
    String errorType,
    String errorMessage,
    Instant occurredAt) {

  public boolean isRegression() {
    return status == IncidentStatus.REGRESSION;
  }
}

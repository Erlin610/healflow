package com.healflow.common.enums;

public enum IncidentStatus {
  OPEN,
  ANALYZING,
  PENDING_REVIEW,
  FIXED,
  REGRESSION,
  IGNORED;

  public boolean canTransitionTo(IncidentStatus target) {
    if (target == null || target == this) {
      return false;
    }
    return switch (this) {
      case OPEN -> target == ANALYZING || target == IGNORED;
      case ANALYZING -> target == PENDING_REVIEW || target == IGNORED;
      case PENDING_REVIEW -> target == FIXED || target == IGNORED;
      case FIXED -> target == REGRESSION;
      case REGRESSION -> target == ANALYZING || target == IGNORED;
      case IGNORED -> false;
    };
  }
}

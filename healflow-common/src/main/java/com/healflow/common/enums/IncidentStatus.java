package com.healflow.common.enums;

public enum IncidentStatus {
  OPEN,
  SKIP,
  ANALYZING,
  PENDING_REVIEW,
  FIXED,
  REGRESSION,
  IGNORED;

  public boolean canTransitionTo(IncidentStatus target) {
    if (target == null || target == this) {
      return false;
    }
    switch (this) {
      case OPEN:
        return target == ANALYZING || target == IGNORED || target == SKIP;
      case SKIP:
        return false;
      case ANALYZING:
        return target == PENDING_REVIEW || target == IGNORED;
      case PENDING_REVIEW:
        return target == FIXED || target == IGNORED;
      case FIXED:
        return target == REGRESSION;
      case REGRESSION:
        return target == ANALYZING || target == IGNORED;
      case IGNORED:
      default:
        return false;
    }
  }
}

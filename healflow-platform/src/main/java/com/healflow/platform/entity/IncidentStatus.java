package com.healflow.platform.entity;

public enum IncidentStatus {
  WAITING,
  ANALYZING,
  ANALYZED,
  FIXING,
  FIXED,
  FAILED,
  NO_ACTION_NEEDED,
  AI_FIXED;

  public boolean canTransitionTo(IncidentStatus target) {
    if (target == null || target == this) {
      return false;
    }
    return switch (this) {
      case WAITING -> target == ANALYZING || target == FAILED;
      case ANALYZING -> target == ANALYZED || target == FAILED;
      case ANALYZED -> target == FIXING || target == FAILED || target == NO_ACTION_NEEDED;
      case FIXING -> target == AI_FIXED || target == FAILED;
      case FIXED, FAILED, NO_ACTION_NEEDED, AI_FIXED -> false;
    };
  }
}


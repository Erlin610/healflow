package com.healflow.platform.entity;

public enum IncidentStatus {
  WAITING,
  ANALYZING,
  ANALYZED,
  FIXING,
  FIXED,
  FAILED;

  public boolean canTransitionTo(IncidentStatus target) {
    if (target == null || target == this) {
      return false;
    }
    return switch (this) {
      case WAITING -> target == ANALYZING || target == FAILED;
      case ANALYZING -> target == ANALYZED || target == FAILED;
      case ANALYZED -> target == FIXING || target == FAILED;
      case FIXING -> target == FIXED || target == FAILED;
      case FIXED, FAILED -> false;
    };
  }
}


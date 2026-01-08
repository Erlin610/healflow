package com.healflow.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SimpleHealflowEngineSandboxTest {

  @Test
  void analyze_returnsHighWhenTokenMatches() {
    HealflowEngine engine = new SimpleHealflowEngine("boom");
    HealingResult result = engine.analyze("something BOOM happened");
    assertEquals(Severity.HIGH, result.severity());
    assertEquals("triage_required", result.message());
  }

  @Test
  void analyze_returnsLowWhenTokenDoesNotMatch() {
    HealflowEngine engine = new SimpleHealflowEngine("boom");
    HealingResult result = engine.analyze("all good");
    assertEquals(Severity.LOW, result.severity());
    assertEquals("no_action", result.message());
  }

  @Test
  void analyze_rejectsBlankReport() {
    HealflowEngine engine = new SimpleHealflowEngine("boom");
    assertThrows(IllegalArgumentException.class, () -> engine.analyze(" "));
  }

  @Test
  void constructor_rejectsBlankToken() {
    assertThrows(IllegalArgumentException.class, () -> new SimpleHealflowEngine(" "));
  }
}

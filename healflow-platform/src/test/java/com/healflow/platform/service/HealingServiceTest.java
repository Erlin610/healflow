package com.healflow.platform.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.healflow.engine.HealflowEngine;
import com.healflow.engine.HealingResult;
import com.healflow.engine.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HealingServiceTest {

  @Mock private HealflowEngine engine;
  @InjectMocks private HealingService service;

  @Test
  void analyzeDelegatesToEngine() {
    String incidentReport = "panic: something bad";
    HealingResult expected = new HealingResult(Severity.HIGH, "triage_required");
    when(engine.analyze(incidentReport)).thenReturn(expected);

    HealingResult actual = service.analyze(incidentReport);

    assertSame(expected, actual);
    verify(engine).analyze(incidentReport);
    verifyNoMoreInteractions(engine);
  }
}


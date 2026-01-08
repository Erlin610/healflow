package com.healflow.starter.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.healflow.starter.config.HealFlowProperties;
import com.healflow.starter.reporter.IncidentReporter;
import org.junit.jupiter.api.Test;

class HealFlowExceptionHandlerTest {

  @Test
  void delegatesToReporter() {
    RecordingIncidentReporter reporter = new RecordingIncidentReporter();
    HealFlowExceptionHandler handler = new HealFlowExceptionHandler(reporter);

    Exception boom = new Exception("boom");
    handler.handleException(boom);

    assertThat(reporter.reported).isSameAs(boom);
  }

  private static final class RecordingIncidentReporter extends IncidentReporter {
    private Throwable reported;

    private RecordingIncidentReporter() {
      super(new HealFlowProperties());
    }

    @Override
    public void report(Throwable ex) {
      this.reported = ex;
    }
  }
}

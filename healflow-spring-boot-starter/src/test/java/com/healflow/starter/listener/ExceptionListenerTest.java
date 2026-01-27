package com.healflow.starter.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.healflow.starter.config.HealFlowProperties;
import com.healflow.starter.reporter.IncidentReporter;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExceptionListenerTest {

  private Thread.UncaughtExceptionHandler originalDefaultHandler;

  @BeforeEach
  void captureDefaultHandler() {
    originalDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
  }

  @AfterEach
  void restoreDefaultHandler() {
    Thread.setDefaultUncaughtExceptionHandler(originalDefaultHandler);
  }

  @Test
  void installsAndRestoresDefaultHandlerAndDelegates() throws Exception {
    AtomicReference<Throwable> delegated = new AtomicReference<>();
    Thread.UncaughtExceptionHandler previousHandler = (t, e) -> delegated.set(e);
    Thread.setDefaultUncaughtExceptionHandler(previousHandler);

    RecordingIncidentReporter reporter = new RecordingIncidentReporter();
    ExceptionListener listener = new ExceptionListener(reporter);

    listener.afterPropertiesSet();
    assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(listener);

    RuntimeException boom = new RuntimeException("boom");
    Thread currentThread = Thread.currentThread();
    assertDoesNotThrow(() -> listener.uncaughtException(currentThread, boom));
    assertThat(reporter.reported).isSameAs(boom);
    assertThat(delegated.get()).isSameAs(boom);

    listener.destroy();
    assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(previousHandler);
  }

  @Test
  void reportingFailureDoesNotBreakHandlerChain() throws Exception {
    AtomicReference<Throwable> delegated = new AtomicReference<>();
    Thread.UncaughtExceptionHandler previousHandler = (t, e) -> delegated.set(e);
    Thread.setDefaultUncaughtExceptionHandler(previousHandler);

    IncidentReporter reporter =
        new IncidentReporter(new HealFlowProperties()) {
          @Override
          public void report(Throwable ex) {
            throw new IllegalStateException("report failed");
          }
        };

    ExceptionListener listener = new ExceptionListener(reporter);
    listener.afterPropertiesSet();

    RuntimeException boom = new RuntimeException("boom");
    assertDoesNotThrow(() -> listener.uncaughtException(Thread.currentThread(), boom));
    assertThat(delegated.get()).isSameAs(boom);
  }

  @Test
  void destroyDoesNotOverrideNewDefaultHandler() throws Exception {
    Thread.UncaughtExceptionHandler previousHandler = (t, e) -> {};
    Thread.setDefaultUncaughtExceptionHandler(previousHandler);

    RecordingIncidentReporter reporter = new RecordingIncidentReporter();
    ExceptionListener listener = new ExceptionListener(reporter);
    listener.afterPropertiesSet();

    Thread.UncaughtExceptionHandler newHandler = (t, e) -> {};
    Thread.setDefaultUncaughtExceptionHandler(newHandler);

    listener.destroy();
    assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(newHandler);
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


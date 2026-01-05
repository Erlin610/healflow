package com.healflow.starter.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.healflow.engine.HealflowEngine;
import com.healflow.engine.HealingResult;
import com.healflow.engine.Severity;
import com.healflow.starter.util.GitPropertiesLoader;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

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

    AtomicReference<String> analyzedReport = new AtomicReference<>();
    HealflowEngine engine =
        incidentReport -> {
          analyzedReport.set(incidentReport);
          return new HealingResult(Severity.LOW, "ok");
        };
    GitPropertiesLoader gitPropertiesLoader = new GitPropertiesLoader(new DefaultResourceLoader());
    ExceptionListener listener = new ExceptionListener(engine, gitPropertiesLoader);

    listener.afterPropertiesSet();
    assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(listener);

    RuntimeException boom = new RuntimeException("boom");
    Thread currentThread = Thread.currentThread();
    assertDoesNotThrow(() -> listener.uncaughtException(currentThread, boom));
    assertThat(delegated.get()).isSameAs(boom);
    assertThat(analyzedReport.get()).contains("thread=" + currentThread.getName());
    assertThat(analyzedReport.get()).contains("commitId=abcdef123456");
    assertThat(analyzedReport.get()).contains("java.lang.RuntimeException: boom");

    listener.destroy();
    assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(previousHandler);
  }

  @Test
  void analysisFailureDoesNotBreakHandlerChain() throws Exception {
    AtomicReference<Throwable> delegated = new AtomicReference<>();
    Thread.UncaughtExceptionHandler previousHandler = (t, e) -> delegated.set(e);
    Thread.setDefaultUncaughtExceptionHandler(previousHandler);

    HealflowEngine engine =
        incidentReport -> {
          throw new IllegalStateException("analysis failed");
        };
    GitPropertiesLoader gitPropertiesLoader = new GitPropertiesLoader(new DefaultResourceLoader());
    ExceptionListener listener = new ExceptionListener(engine, gitPropertiesLoader);
    listener.afterPropertiesSet();

    RuntimeException boom = new RuntimeException("boom");
    assertDoesNotThrow(() -> listener.uncaughtException(Thread.currentThread(), boom));
    assertThat(delegated.get()).isSameAs(boom);
  }

  @Test
  void destroyDoesNotOverrideNewDefaultHandler() throws Exception {
    Thread.UncaughtExceptionHandler previousHandler = (t, e) -> {};
    Thread.setDefaultUncaughtExceptionHandler(previousHandler);

    HealflowEngine engine = incidentReport -> new HealingResult(Severity.LOW, "ok");
    GitPropertiesLoader gitPropertiesLoader = new GitPropertiesLoader(new DefaultResourceLoader());
    ExceptionListener listener = new ExceptionListener(engine, gitPropertiesLoader);
    listener.afterPropertiesSet();

    Thread.UncaughtExceptionHandler newHandler = (t, e) -> {};
    Thread.setDefaultUncaughtExceptionHandler(newHandler);

    listener.destroy();
    assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameAs(newHandler);
  }
}


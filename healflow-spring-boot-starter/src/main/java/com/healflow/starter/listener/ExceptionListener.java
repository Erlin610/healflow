package com.healflow.starter.listener;

import com.healflow.starter.reporter.IncidentReporter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public final class ExceptionListener
    implements Thread.UncaughtExceptionHandler, InitializingBean, DisposableBean {

  private static final Logger log = Logger.getLogger(ExceptionListener.class.getName());

  private final IncidentReporter reporter;

  private volatile Thread.UncaughtExceptionHandler previous;

  public ExceptionListener(IncidentReporter reporter) {
    this.reporter = Objects.requireNonNull(reporter, "reporter");
  }

  @Override
  public void afterPropertiesSet() {
    previous = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(this);
  }

  @Override
  public void destroy() {
    if (Thread.getDefaultUncaughtExceptionHandler() == this) {
      Thread.setDefaultUncaughtExceptionHandler(previous);
    }
  }

  @Override
  public void uncaughtException(Thread thread, Throwable throwable) {
    try {
      reporter.report(throwable);
    } catch (RuntimeException e) {
      log.log(Level.FINE, "Healflow incident reporting failed", e);
    } finally {
      Thread.UncaughtExceptionHandler handler = previous;
      if (handler != null && handler != this) {
        handler.uncaughtException(thread, throwable);
      }
    }
  }
}


package com.healflow.starter.listener;

import com.healflow.engine.HealflowEngine;
import com.healflow.starter.util.GitPropertiesLoader;
import com.healflow.starter.util.GitPropertiesLoader.GitMetadata;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public final class ExceptionListener
    implements Thread.UncaughtExceptionHandler, InitializingBean, DisposableBean {

  private static final Logger log = Logger.getLogger(ExceptionListener.class.getName());

  private final HealflowEngine engine;
  private final GitPropertiesLoader gitPropertiesLoader;

  private volatile Thread.UncaughtExceptionHandler previous;

  public ExceptionListener(HealflowEngine engine, GitPropertiesLoader gitPropertiesLoader) {
    this.engine = Objects.requireNonNull(engine, "engine");
    this.gitPropertiesLoader = Objects.requireNonNull(gitPropertiesLoader, "gitPropertiesLoader");
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
      GitMetadata gitMetadata = gitPropertiesLoader.load();
      String report = formatReport(thread, throwable, gitMetadata);
      engine.analyze(report);
    } catch (RuntimeException e) {
      log.log(Level.FINE, "Healflow exception analysis failed", e);
    } finally {
      Thread.UncaughtExceptionHandler handler = previous;
      if (handler != null && handler != this) {
        handler.uncaughtException(thread, throwable);
      }
    }
  }

  private static String formatReport(Thread thread, Throwable throwable, GitMetadata gitMetadata) {
    StringWriter sw = new StringWriter();
    throwable.printStackTrace(new PrintWriter(sw));
    return "thread=" + thread.getName()
        + "\ncommitId=" + gitMetadata.commitId()
        + "\nbranch=" + gitMetadata.branch()
        + "\nbuildTime=" + gitMetadata.buildTime()
        + "\n"
        + sw;
  }
}

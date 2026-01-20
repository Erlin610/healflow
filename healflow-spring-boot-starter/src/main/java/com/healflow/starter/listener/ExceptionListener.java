package com.healflow.starter.listener;

import com.healflow.engine.HealflowEngine;
import com.healflow.starter.util.GitPropertiesLoader;
import com.healflow.starter.util.GitPropertiesLoader.GitMetadata;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.slf4j.MDC;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
    HttpContext httpContext = captureHttpContext();
    StringBuilder sb = new StringBuilder(256);
    sb.append("thread=").append(thread.getName());
    sb.append("\nbranch=").append(gitMetadata.branch());
    sb.append("\nbuildTime=").append(gitMetadata.buildTime());
    if (httpContext.requestUrl() != null) {
      sb.append("\nrequestUrl=").append(httpContext.requestUrl());
    }
    if (httpContext.requestMethod() != null) {
      sb.append("\nrequestMethod=").append(httpContext.requestMethod());
    }
    if (httpContext.requestParams() != null) {
      sb.append("\nrequestParams=").append(httpContext.requestParams());
    }
    if (httpContext.traceId() != null) {
      sb.append("\ntraceId=").append(httpContext.traceId());
    }
    sb.append('\n').append(sw);
    return sb.toString();
  }

  private static HttpContext captureHttpContext() {
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
      return HttpContext.empty();
    }

    String requestUrl = null;
    String requestMethod = null;
    String requestParams = null;
    String traceId = normalize(MDC.get("traceId"));

    try {
      var request = servletAttributes.getRequest();
      if (request != null) {
        requestUrl = normalize(request.getRequestURL() != null ? request.getRequestURL().toString() : null);
        requestMethod = normalize(request.getMethod());
        requestParams = normalize(request.getQueryString());
        if (traceId == null) {
          traceId = normalize(request.getHeader("X-Trace-Id"));
          if (traceId == null) {
            traceId = normalize(request.getHeader("traceId"));
          }
          if (traceId == null) {
            traceId = normalize(request.getHeader("X-B3-TraceId"));
          }
        }
      }
    } catch (RuntimeException ignored) {
      // Best-effort only.
    }

    return new HttpContext(requestUrl, requestMethod, requestParams, traceId);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private record HttpContext(String requestUrl, String requestMethod, String requestParams, String traceId) {
    private static HttpContext empty() {
      return new HttpContext(null, null, null, null);
    }
  }
}

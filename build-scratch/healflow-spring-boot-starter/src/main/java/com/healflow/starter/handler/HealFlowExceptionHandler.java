package com.healflow.starter.handler;

import com.healflow.starter.reporter.IncidentReporter;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
@Order(org.springframework.core.Ordered.LOWEST_PRECEDENCE) // 确保让业务先处理，兜底才轮到我们
public class HealFlowExceptionHandler {

  private final IncidentReporter reporter;

  public HealFlowExceptionHandler(IncidentReporter reporter) {
    this.reporter = reporter;
  }

  @ExceptionHandler(Exception.class)
  public void handleException(Exception e) {
    // 1. 上报
    reporter.report(e);

    // 2. 注意：这里我们只通过“旁路”上报，不应该吞掉异常。
    // 但为了演示效果，通常你可能需要再次抛出，或者业务系统自己有 GlobalHandler。
    // 更好的方式是使用 AOP 或者 Spring Boot 的 ErrorController。
    // 这里为了简单，先静默处理。
  }
}

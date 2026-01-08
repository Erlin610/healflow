package com.healflow.test;

import java.lang.reflect.Method;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class JacocoDumpExtension implements AfterAllCallback {

  @Override
  public void afterAll(ExtensionContext context) throws Exception {
    try {
      Class<?> runtime = Class.forName("org.jacoco.agent.rt.RT");
      Method getAgent = runtime.getMethod("getAgent");
      Object agent = getAgent.invoke(null);
      Method dump = agent.getClass().getMethod("dump", boolean.class);
      dump.invoke(agent, false);
    } catch (ClassNotFoundException ignored) {
      // No JaCoCo agent attached to this JVM.
    }
  }
}


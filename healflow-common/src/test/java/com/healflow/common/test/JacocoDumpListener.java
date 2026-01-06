package com.healflow.common.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class JacocoDumpListener implements AfterAllCallback {

  @Override
  public void afterAll(ExtensionContext context) {
    // no-op
  }
}

package com.healflow.starter.properties;

import com.healflow.common.validation.Arguments;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "healflow")
public class HealflowProperties {

  private boolean enabled = true;
  private boolean exceptionListenerEnabled = true;

  private String highSeverityToken = "panic";

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isExceptionListenerEnabled() {
    return exceptionListenerEnabled;
  }

  public void setExceptionListenerEnabled(boolean exceptionListenerEnabled) {
    this.exceptionListenerEnabled = exceptionListenerEnabled;
  }

  public String getHighSeverityToken() {
    return highSeverityToken;
  }

  public void setHighSeverityToken(String highSeverityToken) {
    this.highSeverityToken = Arguments.requireNonBlank(highSeverityToken, "highSeverityToken");
  }
}

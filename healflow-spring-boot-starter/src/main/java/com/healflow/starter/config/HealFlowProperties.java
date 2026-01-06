package com.healflow.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "healflow")
public class HealFlowProperties {

  private boolean enabled = true;
  private String serverUrl = "http://localhost:8080"; // Platform 地址
  private String appId;
  private String gitUrl = "";
  private String gitBranch = "main";

  // Getters and Setters (或者使用 Lombok @Data)
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public void setServerUrl(String serverUrl) {
    this.serverUrl = serverUrl;
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getGitUrl() {
    return gitUrl;
  }

  public void setGitUrl(String gitUrl) {
    this.gitUrl = gitUrl == null ? "" : gitUrl;
  }

  public String getGitBranch() {
    return gitBranch;
  }

  public void setGitBranch(String gitBranch) {
    this.gitBranch = (gitBranch == null || gitBranch.isBlank()) ? "main" : gitBranch;
  }
}

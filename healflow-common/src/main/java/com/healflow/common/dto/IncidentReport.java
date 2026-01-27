package com.healflow.common.dto;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * 事故上报 DTO (JDK 21 Record)
 * @param appId 应用ID
 * @param repoUrl Git 仓库地址（可为空）
 * @param branch Git 分支
 * @param errorType 异常类型 (e.g. NullPointerException)
 * @param errorMessage 异常消息
 * @param stackTrace 完整堆栈
 * @param requestUrl 请求 URL（可为空，非 HTTP 场景）
 * @param requestMethod 请求方法（可为空，非 HTTP 场景）
 * @param requestParams 请求参数/QueryString（可为空，非 HTTP 场景）
 * @param traceId 链路ID（可为空）
 * @param environment 环境变量/上下文(e.g. Profile)
 * @param occurredAt 发生时间
 */
public class IncidentReport {

  private String appId;
  private String repoUrl; // optional
  private String branch;
  private String errorType;
  private String errorMessage;
  private String stackTrace;
  private String requestUrl;
  private String requestMethod;
  private String requestParams;
  private String traceId;
  private Map<String, String> environment;
  private Instant occurredAt;

  public IncidentReport() {}

  public IncidentReport(
      String appId,
      String repoUrl,
      String branch,
      String errorType,
      String errorMessage,
      String stackTrace,
      String requestUrl,
      String requestMethod,
      String requestParams,
      String traceId,
      Map<String, String> environment,
      Instant occurredAt) {
    this.appId = appId;
    this.repoUrl = repoUrl;
    this.branch = branch;
    this.errorType = errorType;
    this.errorMessage = errorMessage;
    this.stackTrace = stackTrace;
    this.requestUrl = requestUrl;
    this.requestMethod = requestMethod;
    this.requestParams = requestParams;
    this.traceId = traceId;
    this.environment = environment;
    this.occurredAt = occurredAt;
  }

  // Record-style accessors for source compatibility with previous record usage.
  public String appId() {
    return appId;
  }

  public String repoUrl() {
    return repoUrl;
  }

  public String branch() {
    return branch;
  }

  public String errorType() {
    return errorType;
  }

  public String errorMessage() {
    return errorMessage;
  }

  public String stackTrace() {
    return stackTrace;
  }

  public String requestUrl() {
    return requestUrl;
  }

  public String requestMethod() {
    return requestMethod;
  }

  public String requestParams() {
    return requestParams;
  }

  public String traceId() {
    return traceId;
  }

  public Map<String, String> environment() {
    return environment;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public String getAppId() {
    return appId;
  }

  public void setAppId(String appId) {
    this.appId = appId;
  }

  public String getRepoUrl() {
    return repoUrl;
  }

  public void setRepoUrl(String repoUrl) {
    this.repoUrl = repoUrl;
  }

  public String getBranch() {
    return branch;
  }

  public void setBranch(String branch) {
    this.branch = branch;
  }

  public String getErrorType() {
    return errorType;
  }

  public void setErrorType(String errorType) {
    this.errorType = errorType;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getStackTrace() {
    return stackTrace;
  }

  public void setStackTrace(String stackTrace) {
    this.stackTrace = stackTrace;
  }

  public String getRequestUrl() {
    return requestUrl;
  }

  public void setRequestUrl(String requestUrl) {
    this.requestUrl = requestUrl;
  }

  public String getRequestMethod() {
    return requestMethod;
  }

  public void setRequestMethod(String requestMethod) {
    this.requestMethod = requestMethod;
  }

  public String getRequestParams() {
    return requestParams;
  }

  public void setRequestParams(String requestParams) {
    this.requestParams = requestParams;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public Map<String, String> getEnvironment() {
    return environment;
  }

  public void setEnvironment(Map<String, String> environment) {
    this.environment = environment;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(Instant occurredAt) {
    this.occurredAt = occurredAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof IncidentReport)) {
      return false;
    }
    IncidentReport that = (IncidentReport) o;
    return Objects.equals(appId, that.appId)
        && Objects.equals(repoUrl, that.repoUrl)
        && Objects.equals(branch, that.branch)
        && Objects.equals(errorType, that.errorType)
        && Objects.equals(errorMessage, that.errorMessage)
        && Objects.equals(stackTrace, that.stackTrace)
        && Objects.equals(requestUrl, that.requestUrl)
        && Objects.equals(requestMethod, that.requestMethod)
        && Objects.equals(requestParams, that.requestParams)
        && Objects.equals(traceId, that.traceId)
        && Objects.equals(environment, that.environment)
        && Objects.equals(occurredAt, that.occurredAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        appId,
        repoUrl,
        branch,
        errorType,
        errorMessage,
        stackTrace,
        requestUrl,
        requestMethod,
        requestParams,
        traceId,
        environment,
        occurredAt);
  }

  @Override
  public String toString() {
    return "IncidentReport["
        + "appId="
        + appId
        + ", repoUrl="
        + repoUrl
        + ", branch="
        + branch
        + ", errorType="
        + errorType
        + ", errorMessage="
        + errorMessage
        + ", stackTrace="
        + stackTrace
        + ", requestUrl="
        + requestUrl
        + ", requestMethod="
        + requestMethod
        + ", requestParams="
        + requestParams
        + ", traceId="
        + traceId
        + ", environment="
        + environment
        + ", occurredAt="
        + occurredAt
        + ']';
  }
}

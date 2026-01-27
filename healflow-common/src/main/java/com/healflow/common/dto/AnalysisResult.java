package com.healflow.common.dto;

import java.util.Objects;

public class AnalysisResult {

  private String sessionId;
  private String structuredOutput;
  private String fullText;

  public AnalysisResult() {}

  public AnalysisResult(String sessionId, String structuredOutput, String fullText) {
    this.sessionId = sessionId;
    this.structuredOutput = structuredOutput;
    this.fullText = fullText;
  }

  // Record-style accessors for source compatibility with previous record usage.
  public String sessionId() {
    return sessionId;
  }

  public String structuredOutput() {
    return structuredOutput;
  }

  public String fullText() {
    return fullText;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getStructuredOutput() {
    return structuredOutput;
  }

  public void setStructuredOutput(String structuredOutput) {
    this.structuredOutput = structuredOutput;
  }

  public String getFullText() {
    return fullText;
  }

  public void setFullText(String fullText) {
    this.fullText = fullText;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof AnalysisResult)) {
      return false;
    }
    AnalysisResult that = (AnalysisResult) o;
    return Objects.equals(sessionId, that.sessionId)
        && Objects.equals(structuredOutput, that.structuredOutput)
        && Objects.equals(fullText, that.fullText);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sessionId, structuredOutput, fullText);
  }

  @Override
  public String toString() {
    return "AnalysisResult["
        + "sessionId="
        + sessionId
        + ", structuredOutput="
        + structuredOutput
        + ", fullText="
        + fullText
        + ']';
  }
}

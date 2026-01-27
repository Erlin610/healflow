package com.healflow.common.dto;

import java.util.Objects;

public class FixResult {

  private String result;
  private String usage;

  public FixResult() {}

  public FixResult(String result, String usage) {
    this.result = result;
    this.usage = usage;
  }

  // Record-style accessors for source compatibility with previous record usage.
  public String result() {
    return result;
  }

  public String usage() {
    return usage;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public String getUsage() {
    return usage;
  }

  public void setUsage(String usage) {
    this.usage = usage;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FixResult)) {
      return false;
    }
    FixResult fixResult = (FixResult) o;
    return Objects.equals(result, fixResult.result) && Objects.equals(usage, fixResult.usage);
  }

  @Override
  public int hashCode() {
    return Objects.hash(result, usage);
  }

  @Override
  public String toString() {
    return "FixResult[" + "result=" + result + ", usage=" + usage + ']';
  }
}

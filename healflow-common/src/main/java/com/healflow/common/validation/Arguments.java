package com.healflow.common.validation;

import java.util.Objects;

public final class Arguments {

  private Arguments() {}

  public static <T> T requireNonNull(T value, String name) {
    String parameterName = requireName(name);
    return Objects.requireNonNull(value, parameterName + " must not be null");
  }

  public static String requireNonBlank(String value, String name) {
    String parameterName = requireName(name);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(parameterName + " must not be blank");
    }
    return value;
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    return name;
  }
}


package com.healflow.common.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArgumentsTest {

  @Test
  void requireNonNull_acceptsValue() {
    String value = Arguments.requireNonNull("x", "value");
    assertEquals("x", value);
  }

  @Test
  void requireNonNull_rejectsNullValue() {
    NullPointerException ex = assertThrows(NullPointerException.class, () -> Arguments.requireNonNull(null, "value"));
    assertEquals("value must not be null", ex.getMessage());
  }

  @Test
  void requireNonNull_rejectsBlankName() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Arguments.requireNonNull("x", " "));
    assertEquals("name must not be blank", ex.getMessage());
  }

  @Test
  void requireNonBlank_acceptsNonBlank() {
    String value = Arguments.requireNonBlank("x", "value");
    assertNotNull(value);
    assertEquals("x", value);
  }

  @Test
  void requireNonBlank_rejectsBlankValue() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Arguments.requireNonBlank(" ", "value"));
    assertEquals("value must not be blank", ex.getMessage());
  }
}


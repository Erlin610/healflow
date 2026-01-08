package com.healflow.common.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ArgumentsDtoTest {

  @Test
  void requireNonBlank_rejectsBlankValue() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Arguments.requireNonBlank(" ", "value"));
    assertEquals("value must not be blank", ex.getMessage());
  }

  @Test
  void requireNonNull_rejectsBlankName() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> Arguments.requireNonNull("x", " "));
    assertEquals("name must not be blank", ex.getMessage());
  }
}


package com.healflow.platform.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EncryptionUtilTest {

  private static final String KEY = "0123456789abcdef0123456789abcdef";

  @Test
  void encryptDecryptRoundTrip() {
    EncryptionUtil util = new EncryptionUtil(KEY);

    String encryptedOne = util.encrypt("secret");
    String encryptedTwo = util.encrypt("secret");

    assertNotEquals("secret", encryptedOne);
    assertNotEquals(encryptedOne, encryptedTwo);
    assertEquals("secret", util.decrypt(encryptedOne));
    assertEquals("secret", util.decrypt(encryptedTwo));
  }

  @Test
  void handlesNullInputs() {
    EncryptionUtil util = new EncryptionUtil(KEY);

    assertNull(util.encrypt(null));
    assertNull(util.decrypt(null));
  }

  @Test
  void rejectsBlankKey() {
    assertThrows(IllegalStateException.class, () -> new EncryptionUtil(" "));
  }
}

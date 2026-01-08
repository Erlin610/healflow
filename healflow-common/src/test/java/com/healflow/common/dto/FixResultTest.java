package com.healflow.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FixResultTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void constructor_exposesAccessors() {
    FixResult result = new FixResult("ok", "tokens: 12");

    assertEquals("ok", result.result());
    assertEquals("tokens: 12", result.usage());
  }

  @Test
  void constructor_acceptsNullFields() {
    FixResult result = new FixResult(null, null);

    assertNull(result.result());
    assertNull(result.usage());
  }

  @Test
  void valueSemantics_equalsHashCodeToString() {
    FixResult first = new FixResult("ok", "u");
    FixResult second = new FixResult("ok", "u");
    FixResult third = new FixResult("ok", "other");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, third);

    String text = first.toString();
    assertNotNull(text);
    assertTrue(text.contains("FixResult"));
    assertTrue(text.contains("usage"));
  }

  @Test
  void json_roundTrip() throws Exception {
    FixResult result = new FixResult("ok", "tokens: 12");

    String json = mapper.writeValueAsString(result);
    JsonNode node = mapper.readTree(json);
    assertEquals("ok", node.get("result").asText());
    assertEquals("tokens: 12", node.get("usage").asText());

    FixResult parsed = mapper.readValue(json, FixResult.class);
    assertEquals(result, parsed);
  }

  @Test
  void json_backwardsCompatibility_missingUsageDeserializesToNull() throws Exception {
    String legacyJson =
        """
        {
          "result": "ok"
        }
        """;

    FixResult parsed = mapper.readValue(legacyJson, FixResult.class);
    assertEquals("ok", parsed.result());
    assertNull(parsed.usage());
  }
}


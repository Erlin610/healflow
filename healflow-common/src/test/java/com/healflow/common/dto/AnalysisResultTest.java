package com.healflow.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnalysisResultTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void constructor_exposesAccessors() {
    AnalysisResult result = new AnalysisResult("sid", "{}", "full");

    assertEquals("sid", result.sessionId());
    assertEquals("{}", result.structuredOutput());
    assertEquals("full", result.fullText());
  }

  @Test
  void constructor_acceptsNullFields() {
    AnalysisResult result = new AnalysisResult(null, null, null);

    assertNull(result.sessionId());
    assertNull(result.structuredOutput());
    assertNull(result.fullText());
  }

  @Test
  void valueSemantics_equalsHashCodeToString() {
    AnalysisResult first = new AnalysisResult("sid", "{}", "full");
    AnalysisResult second = new AnalysisResult("sid", "{}", "full");
    AnalysisResult third = new AnalysisResult("sid", "{}", "other");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, third);

    String text = first.toString();
    assertNotNull(text);
    assertTrue(text.contains("AnalysisResult"));
    assertTrue(text.contains("sessionId"));
  }

  @Test
  void json_roundTrip() throws Exception {
    AnalysisResult result = new AnalysisResult("sid", "{\"key\":\"value\"}", "full");

    String json = mapper.writeValueAsString(result);
    JsonNode node = mapper.readTree(json);
    assertEquals("sid", node.get("sessionId").asText());
    assertEquals("{\"key\":\"value\"}", node.get("structuredOutput").asText());
    assertEquals("full", node.get("fullText").asText());

    AnalysisResult parsed = mapper.readValue(json, AnalysisResult.class);
    assertEquals(result, parsed);
  }

  @Test
  void json_backwardsCompatibility_missingFullTextDeserializesToNull() throws Exception {
    String legacyJson =
        """
        {
          "sessionId": "sid",
          "structuredOutput": "{}"
        }
        """;

    AnalysisResult parsed = mapper.readValue(legacyJson, AnalysisResult.class);
    assertEquals("sid", parsed.sessionId());
    assertEquals("{}", parsed.structuredOutput());
    assertNull(parsed.fullText());
  }
}


package com.healflow.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class FixProposalTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void constructor_exposesAccessors() {
    FixProposal proposal = new FixProposal("sid", "{}", "full");

    assertEquals("sid", proposal.sessionId());
    assertEquals("{}", proposal.structuredOutput());
    assertEquals("full", proposal.fullText());
  }

  @Test
  void constructor_acceptsNullFields() {
    FixProposal proposal = new FixProposal(null, null, null);

    assertNull(proposal.sessionId());
    assertNull(proposal.structuredOutput());
    assertNull(proposal.fullText());
  }

  @Test
  void valueSemantics_equalsHashCodeToString() {
    FixProposal first = new FixProposal("sid", "{}", "full");
    FixProposal second = new FixProposal("sid", "{}", "full");
    FixProposal third = new FixProposal("sid", "{}", "other");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, third);

    String text = first.toString();
    assertNotNull(text);
    assertTrue(text.contains("FixProposal"));
    assertTrue(text.contains("structuredOutput"));
  }

  @Test
  void json_roundTrip() throws Exception {
    FixProposal proposal = new FixProposal("sid", "{\"diff\":\"...\"}", "full");

    String json = mapper.writeValueAsString(proposal);
    JsonNode node = mapper.readTree(json);
    assertEquals("sid", node.get("sessionId").asText());
    assertEquals("{\"diff\":\"...\"}", node.get("structuredOutput").asText());
    assertEquals("full", node.get("fullText").asText());

    FixProposal parsed = mapper.readValue(json, FixProposal.class);
    assertEquals(proposal, parsed);
  }

  @Test
  void json_backwardsCompatibility_missingStructuredOutputDeserializesToNull() throws Exception {
    String legacyJson =
        """
        {
          "sessionId": "sid",
          "fullText": "full"
        }
        """;

    FixProposal parsed = mapper.readValue(legacyJson, FixProposal.class);
    assertEquals("sid", parsed.sessionId());
    assertNull(parsed.structuredOutput());
    assertEquals("full", parsed.fullText());
  }
}


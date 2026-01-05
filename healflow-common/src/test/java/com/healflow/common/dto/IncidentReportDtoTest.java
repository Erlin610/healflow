package com.healflow.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IncidentReportDtoTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void constructor_rejectsBlankFields() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> new IncidentReportDto(" ", "x", "y"));
    assertEquals("appName must not be blank", ex.getMessage());
  }

  @Test
  void builder_buildsAndValidates() {
    IncidentReportDto report =
        IncidentReportDto.builder()
            .appName("order-service")
            .stackTrace("boom")
            .commitId("abc123")
            .build();

    assertEquals("order-service", report.appName());
    assertEquals("boom", report.stackTrace());
    assertEquals("abc123", report.commitId());
  }

  @Test
  void valueSemantics_equalsHashCodeToString() {
    IncidentReportDto first = new IncidentReportDto("a", "b", "c");
    IncidentReportDto second = new IncidentReportDto("a", "b", "c");
    IncidentReportDto third = new IncidentReportDto("a", "b", "d");

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, third);

    String text = first.toString();
    assertNotNull(text);
    assertTrue(text.contains("IncidentReportDto"));
    assertTrue(text.contains("appName"));
  }

  @Test
  void json_roundTrip() throws Exception {
    IncidentReportDto report = new IncidentReportDto("order-service", "boom", "abc123");

    String json = mapper.writeValueAsString(report);
    JsonNode node = mapper.readTree(json);
    assertEquals("order-service", node.get("appName").asText());
    assertEquals("boom", node.get("stackTrace").asText());
    assertEquals("abc123", node.get("commitId").asText());

    IncidentReportDto parsed = mapper.readValue(json, IncidentReportDto.class);
    assertEquals(report, parsed);
  }
}


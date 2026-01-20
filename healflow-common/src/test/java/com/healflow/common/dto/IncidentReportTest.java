package com.healflow.common.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IncidentReportTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void constructor_acceptsNullRepoUrl() {
    IncidentReport report =
        new IncidentReport(
            "order-service",
            null,
            "main",
            "Boom",
            "message",
            "trace",
            null,
            null,
            null,
            null,
            Map.of(),
            Instant.EPOCH);

    assertNull(report.repoUrl());
    assertEquals("order-service", report.appId());
    assertEquals("main", report.branch());
  }

  @Test
  void valueSemantics_equalsHashCodeToString() {
    IncidentReport first =
        new IncidentReport(
            "app",
            "https://example.com/org/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "trace",
            "http://example.test/api/orders",
            "GET",
            "id=1",
            "trace-1",
            Map.of("profile", "prod"),
            Instant.EPOCH);
    IncidentReport second =
        new IncidentReport(
            "app",
            "https://example.com/org/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "trace",
            "http://example.test/api/orders",
            "GET",
            "id=1",
            "trace-1",
            Map.of("profile", "prod"),
            Instant.EPOCH);
    IncidentReport third =
        new IncidentReport(
            "app",
            "https://example.com/org/other.git",
            "main",
            "NullPointerException",
            "boom",
            "trace",
            "http://example.test/api/orders",
            "GET",
            "id=1",
            "trace-1",
            Map.of("profile", "prod"),
            Instant.EPOCH);

    assertEquals(first, second);
    assertEquals(first.hashCode(), second.hashCode());
    assertNotEquals(first, third);

    String text = first.toString();
    assertNotNull(text);
    assertTrue(text.contains("IncidentReport"));
    assertTrue(text.contains("repoUrl"));
  }

  @Test
  void json_roundTrip_withRepoUrl() throws Exception {
    IncidentReport report =
        new IncidentReport(
            "order-service",
            "not a url",
            "main",
            "NullPointerException",
            "boom",
            "trace",
            "http://example.test/api/orders",
            "POST",
            "id=1",
            "trace-xyz",
            Map.of("profile", "prod"),
            null);

    String json = mapper.writeValueAsString(report);
    JsonNode node = mapper.readTree(json);
    assertEquals("order-service", node.get("appId").asText());
    assertEquals("not a url", node.get("repoUrl").asText());
    assertEquals("main", node.get("branch").asText());
    assertEquals("http://example.test/api/orders", node.get("requestUrl").asText());
    assertEquals("POST", node.get("requestMethod").asText());
    assertEquals("id=1", node.get("requestParams").asText());
    assertEquals("trace-xyz", node.get("traceId").asText());

    IncidentReport parsed = mapper.readValue(json, IncidentReport.class);
    assertEquals(report, parsed);
  }

  @Test
  void json_backwardsCompatibility_missingRepoUrlDeserializesToNull() throws Exception {
    String legacyJson =
        """
        {
          "appId": "order-service",
          "branch": "main",
          "errorType": "NullPointerException",
          "errorMessage": "boom",
          "stackTrace": "trace",
          "environment": {"profile": "prod"},
          "occurredAt": null
        }
        """;

    IncidentReport parsed = mapper.readValue(legacyJson, IncidentReport.class);
    assertEquals("order-service", parsed.appId());
    assertNull(parsed.repoUrl());
    assertEquals("main", parsed.branch());
    assertNull(parsed.requestUrl());
    assertNull(parsed.requestMethod());
    assertNull(parsed.requestParams());
    assertNull(parsed.traceId());
  }
}

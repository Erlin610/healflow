package com.healflow.platform.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.common.dto.IncidentReport;
import com.healflow.platform.service.IncidentService;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private IncidentService incidentService;

  @Test
  void receiveReportDelegatesToService() throws Exception {
    IncidentReport report =
        new IncidentReport(
            "app-123",
            "https://example.invalid/repo.git",
            "main",
            "NullPointerException",
            "boom",
            "stack",
            Map.of("env", "test"),
            Instant.parse("2026-01-05T00:00:00Z"));

    mockMvc
        .perform(
            post("/api/v1/incidents/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isOk())
        .andExpect(content().string("RECEIVED"));

    verify(incidentService).processIncident(report);
  }
}

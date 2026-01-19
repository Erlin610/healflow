package com.healflow.platform.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.common.dto.AnalysisResult;
import com.healflow.common.dto.FixProposal;
import com.healflow.common.dto.FixResult;
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

    when(incidentService.createIncident(report)).thenReturn("inc-123");

    mockMvc
        .perform(
            post("/api/v1/incidents/report")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.incidentId").value("inc-123"))
        .andExpect(jsonPath("$.status").value("OPEN"));

    verify(incidentService).createIncident(report);
  }

  @Test
  void deleteAllIncidentsDelegatesToService() throws Exception {
    when(incidentService.deleteAllIncidents()).thenReturn(3L);

    mockMvc
        .perform(delete("/api/v1/incidents/all"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.deletedCount").value(3));

    verify(incidentService).deleteAllIncidents();
  }

  @Test
  void analyzeGenerateFixAndApplyFixDelegateToService() throws Exception {
    String incidentId = "inc-workflow";
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

    when(incidentService.analyzeIncident(eq(incidentId), eq(report)))
        .thenReturn(new AnalysisResult("sess-1", "{\"ok\":true}", "analysis"));

    mockMvc
        .perform(
            post("/api/v1/incidents/{id}/analyze", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("sess-1"));

    when(incidentService.generateFix(eq(incidentId)))
        .thenReturn(new FixProposal("sess-1", "{\"fix\":true}", "proposal"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/generate-fix", incidentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("sess-1"));

    verify(incidentService).generateFix(incidentId);

    when(incidentService.applyFix(eq(incidentId)))
        .thenReturn(new FixResult("APPLIED", "usage"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/apply-fix", incidentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("APPLIED"));

    verify(incidentService).applyFix(incidentId);
  }

  @Test
  void generateFixReturnsBadRequestWhenServiceRejectsState() throws Exception {
    when(incidentService.generateFix(eq("missing"))).thenThrow(new IllegalStateException("missing"));
    mockMvc
        .perform(post("/api/v1/incidents/{id}/generate-fix", "missing"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void applyFixReturnsBadRequestWhenServiceRejectsState() throws Exception {
    when(incidentService.applyFix(eq("missing"))).thenThrow(new IllegalStateException("missing"));
    mockMvc
        .perform(post("/api/v1/incidents/{id}/apply-fix", "missing"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void analyzeReturnsInternalServerErrorWhenServiceThrows() throws Exception {
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

    when(incidentService.analyzeIncident(eq("inc-workspace-failure"), eq(report)))
        .thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(
            post("/api/v1/incidents/{id}/analyze", "inc-workspace-failure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void generateFixReturnsInternalServerErrorWhenServiceThrows() throws Exception {
    when(incidentService.generateFix(eq("inc-generate-fix-failure")))
        .thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/generate-fix", "inc-generate-fix-failure"))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void applyFixReturnsInternalServerErrorWhenServiceThrows() throws Exception {
    when(incidentService.applyFix(eq("inc-apply-fix-failure")))
        .thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/apply-fix", "inc-apply-fix-failure"))
        .andExpect(status().isInternalServerError());
  }
}

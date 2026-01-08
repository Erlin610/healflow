package com.healflow.platform.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.common.dto.AnalysisResult;
import com.healflow.common.dto.FixProposal;
import com.healflow.common.dto.FixResult;
import com.healflow.common.dto.IncidentReport;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.platform.service.IncidentService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
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
  @MockBean private GitWorkspaceManager gitWorkspaceManager;

  @TempDir Path tempDir;

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

  @Test
  void analyzeUsesPreparedWorkspaceAndPersistsStateForNextStages() throws Exception {
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

    Path preparedWorkspace = tempDir.resolve("app-123");
    when(gitWorkspaceManager.prepareWorkspace(eq("app-123"), eq("https://example.invalid/repo.git"), eq("main")))
        .thenReturn(preparedWorkspace);
    when(incidentService.analyzeIncident(eq(report)))
        .thenReturn(new AnalysisResult("sess-1", "{\"ok\":true}", "analysis"));

    mockMvc
        .perform(
            post("/api/v1/incidents/{id}/analyze", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("sess-1"));

    when(incidentService.generateFix(eq("sess-1"), eq(preparedWorkspace)))
        .thenReturn(new FixProposal("sess-1", "{\"fix\":true}", "proposal"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/generate-fix", incidentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value("sess-1"));

    verify(incidentService).generateFix("sess-1", preparedWorkspace);

    when(incidentService.applyFix(eq("sess-1"), eq(preparedWorkspace)))
        .thenReturn(new FixResult("APPLIED", "usage"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/apply-fix", incidentId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.result").value("APPLIED"));

    verify(incidentService).applyFix("sess-1", preparedWorkspace);

    mockMvc
        .perform(post("/api/v1/incidents/{id}/apply-fix", incidentId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void generateFixWithoutAnalyzeReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/api/v1/incidents/{id}/generate-fix", "missing"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(incidentService);
  }

  @Test
  void applyFixWithoutAnalyzeReturnsBadRequest() throws Exception {
    mockMvc
        .perform(post("/api/v1/incidents/{id}/apply-fix", "missing"))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(incidentService);
  }

  @Test
  void analyzeReturnsInternalServerErrorWhenWorkspacePreparationFails() throws Exception {
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

    when(gitWorkspaceManager.prepareWorkspace(eq("app-123"), eq("https://example.invalid/repo.git"), eq("main")))
        .thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(
            post("/api/v1/incidents/{id}/analyze", "inc-workspace-failure")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isInternalServerError());

    verifyNoInteractions(incidentService);
  }

  @Test
  void analyzeDoesNotPersistStateWhenServiceFails() throws Exception {
    String incidentId = "inc-analyze-service-failure";
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

    Path preparedWorkspace = tempDir.resolve("app-123");
    when(gitWorkspaceManager.prepareWorkspace(eq("app-123"), eq("https://example.invalid/repo.git"), eq("main")))
        .thenReturn(preparedWorkspace);
    when(incidentService.analyzeIncident(eq(report))).thenThrow(new RuntimeException("service boom"));

    mockMvc
        .perform(
            post("/api/v1/incidents/{id}/analyze", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isInternalServerError());

    mockMvc
        .perform(post("/api/v1/incidents/{id}/generate-fix", incidentId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void generateFixReturnsInternalServerErrorWhenServiceThrows() throws Exception {
    String incidentId = "inc-generate-fix-failure";
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

    Path preparedWorkspace = tempDir.resolve("app-123");
    when(gitWorkspaceManager.prepareWorkspace(eq("app-123"), eq("https://example.invalid/repo.git"), eq("main")))
        .thenReturn(preparedWorkspace);
    when(incidentService.analyzeIncident(eq(report)))
        .thenReturn(new AnalysisResult("sess-1", "{\"ok\":true}", "analysis"));

    mockMvc
        .perform(
            post("/api/v1/incidents/{id}/analyze", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isOk());

    when(incidentService.generateFix(eq("sess-1"), eq(preparedWorkspace)))
        .thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/generate-fix", incidentId))
        .andExpect(status().isInternalServerError());
  }

  @Test
  void applyFixReturnsInternalServerErrorWhenServiceThrows() throws Exception {
    String incidentId = "inc-apply-fix-failure";
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

    Path preparedWorkspace = tempDir.resolve("app-123");
    when(gitWorkspaceManager.prepareWorkspace(eq("app-123"), eq("https://example.invalid/repo.git"), eq("main")))
        .thenReturn(preparedWorkspace);
    when(incidentService.analyzeIncident(eq(report)))
        .thenReturn(new AnalysisResult("sess-1", "{\"ok\":true}", "analysis"));

    mockMvc
        .perform(
            post("/api/v1/incidents/{id}/analyze", incidentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(report)))
        .andExpect(status().isOk());

    when(incidentService.applyFix(eq("sess-1"), eq(preparedWorkspace)))
        .thenThrow(new RuntimeException("boom"));

    mockMvc
        .perform(post("/api/v1/incidents/{id}/apply-fix", incidentId))
        .andExpect(status().isInternalServerError());
  }
}

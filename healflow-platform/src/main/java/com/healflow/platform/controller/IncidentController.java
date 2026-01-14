package com.healflow.platform.controller;

import com.healflow.common.dto.AnalysisResult;
import com.healflow.common.dto.FixProposal;
import com.healflow.common.dto.FixResult;
import com.healflow.common.dto.IncidentReport;
import com.healflow.platform.service.IncidentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);
    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @PostMapping("/report")
    public ResponseEntity<Map<String, String>> receiveReport(@RequestBody IncidentReport report) {
        log.info("Received report for app: {}", report.appId());
        String incidentId = incidentService.createIncident(report);
        return ResponseEntity.ok(Map.of("incidentId", incidentId, "status", "OPEN"));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listIncidents(
            @RequestParam(required = false) String status) {
        log.info("Listing incidents, status filter: {}", status);
        List<Map<String, Object>> incidents = incidentService.listIncidents(status);
        return ResponseEntity.ok(incidents);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getIncident(@PathVariable String id) {
        log.info("Getting incident: {}", id);
        try {
            Map<String, Object> incident = incidentService.getIncidentDetails(id);
            return ResponseEntity.ok(incident);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ========== Phase 5: Multi-stage AI Workflow Endpoints ==========

    @PostMapping("/{id}/analyze")
    public ResponseEntity<AnalysisResult> analyze(@PathVariable String id, @RequestBody IncidentReport report) {
        log.info("Starting analysis for incident: {}", id);
        try {
            AnalysisResult result = incidentService.analyzeIncident(id, report);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Analysis failed for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/generate-fix")
    public ResponseEntity<FixProposal> generateFix(@PathVariable String id) {
        log.info("Generating fix for incident: {}", id);
        try {
            FixProposal proposal = incidentService.generateFix(id);
            return ResponseEntity.ok(proposal);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Fix generation failed for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/apply-fix")
    public ResponseEntity<FixResult> applyFix(@PathVariable String id) {
        log.info("Applying fix for incident: {}", id);
        try {
            FixResult result = incidentService.applyFix(id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Fix application failed for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/start-fix")
    public ResponseEntity<Map<String, Object>> startFix(
            @PathVariable String id,
            @RequestBody Map<String, Object> request) {
        log.info("Starting fix for incident: {} with answers: {}", id, request.get("answers"));
        try {
            Object answers = request.get("answers");
            String additionalInfo = request.get("additionalInfo") != null
                ? request.get("additionalInfo").toString()
                : "";
            incidentService.startFixWithAnswers(id, answers, additionalInfo);
            return ResponseEntity.ok(Map.of("status", "PENDING_REVIEW"));
        } catch (Exception e) {
            log.error("Failed to start fix for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/no-action")
    public ResponseEntity<Map<String, Object>> markNoAction(@PathVariable String id) {
        log.info("Marking incident as no action needed: {}", id);
        try {
            incidentService.markNoActionNeeded(id);
            return ResponseEntity.ok(Map.of("status", "IGNORED"));
        } catch (Exception e) {
            log.error("Failed to mark no action for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

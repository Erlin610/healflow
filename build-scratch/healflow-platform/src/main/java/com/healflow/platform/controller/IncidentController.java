package com.healflow.platform.controller;

import com.healflow.common.dto.AnalysisResult;
import com.healflow.common.dto.FixProposal;
import com.healflow.common.dto.FixResult;
import com.healflow.common.dto.IncidentReport;
import com.healflow.engine.git.GitWorkspaceManager;
import com.healflow.platform.service.IncidentService;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);
    private final IncidentService incidentService;
    private final GitWorkspaceManager gitManager;
    private final Map<String, String> sessionStore = new ConcurrentHashMap<>();
    private final Map<String, Path> workspaceStore = new ConcurrentHashMap<>();

    public IncidentController(IncidentService incidentService, GitWorkspaceManager gitManager) {
        this.incidentService = incidentService;
        this.gitManager = gitManager;
    }

    @PostMapping("/report")
    public String receiveReport(@RequestBody IncidentReport report) {
        log.info("Received report for app: {}", report.appId());
        incidentService.processIncident(report);
        return "RECEIVED";
    }

    // ========== Phase 5: Multi-stage AI Workflow Endpoints ==========

    @PostMapping("/{id}/analyze")
    public ResponseEntity<AnalysisResult> analyze(@PathVariable String id, @RequestBody IncidentReport report) {
        log.info("Starting analysis for incident: {}", id);
        try {
            Path workspace = gitManager.prepareWorkspace(report.appId(), report.repoUrl(), report.branch());
            AnalysisResult result = incidentService.analyzeIncident(report);
            sessionStore.put(id, result.sessionId());
            workspaceStore.put(id, workspace);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Analysis failed for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/generate-fix")
    public ResponseEntity<FixProposal> generateFix(@PathVariable String id) {
        log.info("Generating fix for incident: {}", id);
        try {
            String sessionId = sessionStore.get(id);
            Path workspace = workspaceStore.get(id);
            if (sessionId == null || workspace == null) {
                return ResponseEntity.badRequest().build();
            }
            FixProposal proposal = incidentService.generateFix(sessionId, workspace);
            return ResponseEntity.ok(proposal);
        } catch (Exception e) {
            log.error("Fix generation failed for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{id}/apply-fix")
    public ResponseEntity<FixResult> applyFix(@PathVariable String id) {
        log.info("Applying fix for incident: {}", id);
        try {
            String sessionId = sessionStore.get(id);
            Path workspace = workspaceStore.get(id);
            if (sessionId == null || workspace == null) {
                return ResponseEntity.badRequest().build();
            }
            FixResult result = incidentService.applyFix(sessionId, workspace);
            sessionStore.remove(id);
            workspaceStore.remove(id);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Fix application failed for incident: {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }
}

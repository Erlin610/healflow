package com.healflow.platform.controller;

import com.healflow.engine.HealingResult;
import com.healflow.platform.service.HealingService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReportController {

  private static final Map<String, String> STATUS_UP = Map.of("status", "UP");

  private final HealingService healingService;

  public ReportController(HealingService healingService) {
    this.healingService = healingService;
  }

  @GetMapping("/status")
  public Map<String, String> status() {
    return STATUS_UP;
  }

  @PostMapping("/report")
  public HealingResult report(@Valid @RequestBody ReportRequest request) {
    return healingService.analyze(request.incidentReport());
  }
}

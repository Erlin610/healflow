package com.healflow.platform.service;

import com.healflow.engine.HealflowEngine;
import com.healflow.engine.HealingResult;
import org.springframework.stereotype.Service;

@Service
public class HealingService {

  private final HealflowEngine engine;

  public HealingService(HealflowEngine engine) {
    this.engine = engine;
  }

  public HealingResult analyze(String incidentReport) {
    return engine.analyze(incidentReport);
  }
}


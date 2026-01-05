package com.healflow.platform.config;

import com.healflow.engine.HealflowEngine;
import com.healflow.engine.SimpleHealflowEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EngineConfiguration {

  @Bean
  public HealflowEngine healflowEngine() {
    return new SimpleHealflowEngine();
  }
}

package com.healflow.starter.config;

import com.healflow.starter.handler.HealFlowExceptionHandler;
import com.healflow.starter.reporter.IncidentReporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(HealFlowProperties.class)
@ConditionalOnProperty(prefix = "healflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealFlowAutoConfiguration {

  @Bean
  public IncidentReporter incidentReporter(HealFlowProperties properties) {
    return new IncidentReporter(properties);
  }

  @Bean
  public HealFlowExceptionHandler healFlowExceptionHandler(IncidentReporter reporter) {
    return new HealFlowExceptionHandler(reporter);
  }
}

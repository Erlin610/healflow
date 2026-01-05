package com.healflow.starter.autoconfigure;

import com.healflow.engine.HealflowEngine;
import com.healflow.engine.SimpleHealflowEngine;
import com.healflow.starter.listener.ExceptionListener;
import com.healflow.starter.properties.HealflowProperties;
import com.healflow.starter.util.GitPropertiesLoader;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.ResourceLoader;

@AutoConfiguration
@ConditionalOnClass(HealflowEngine.class)
@EnableConfigurationProperties(HealflowProperties.class)
@ConditionalOnProperty(prefix = "healflow", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HealflowAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public HealflowEngine healflowEngine(HealflowProperties properties) {
    return new SimpleHealflowEngine(properties.getHighSeverityToken());
  }

  @Bean
  @ConditionalOnMissingBean
  public GitPropertiesLoader gitPropertiesLoader(ResourceLoader resourceLoader) {
    return new GitPropertiesLoader(resourceLoader);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      prefix = "healflow",
      name = "exception-listener-enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ExceptionListener exceptionListener(HealflowEngine engine, GitPropertiesLoader gitPropertiesLoader) {
    return new ExceptionListener(engine, gitPropertiesLoader);
  }
}

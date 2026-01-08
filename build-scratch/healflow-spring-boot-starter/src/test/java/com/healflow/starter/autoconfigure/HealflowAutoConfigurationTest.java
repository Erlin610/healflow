package com.healflow.starter.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.healflow.engine.HealflowEngine;
import com.healflow.engine.HealingResult;
import com.healflow.engine.Severity;
import com.healflow.starter.listener.ExceptionListener;
import com.healflow.starter.util.GitPropertiesLoader;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HealflowAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(HealflowAutoConfiguration.class));

  @Test
  void providesEngineByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(HealflowEngine.class);
          assertThat(context).hasSingleBean(GitPropertiesLoader.class);
          assertThat(context).hasSingleBean(ExceptionListener.class);
          HealflowEngine engine = context.getBean(HealflowEngine.class);
          HealingResult result = engine.analyze("panic: something bad");
          assertThat(result.severity()).isEqualTo(Severity.HIGH);
        });
  }

  @Test
  void bindsPropertiesAndUsesToken() {
    contextRunner
        .withPropertyValues("healflow.high-severity-token=boom")
        .run(
            context -> {
              HealflowEngine engine = context.getBean(HealflowEngine.class);
              assertThat(engine.analyze("BOOM").severity()).isEqualTo(Severity.HIGH);
              assertThat(engine.analyze("panic").severity()).isEqualTo(Severity.LOW);
            });
  }

  @Test
  void canBeDisabled() {
    contextRunner.withPropertyValues("healflow.enabled=false").run(context -> assertThat(context).doesNotHaveBean(HealflowEngine.class));
  }

  @Test
  void canDisableExceptionListenerOnly() {
    contextRunner
        .withPropertyValues("healflow.exception-listener-enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(HealflowEngine.class);
              assertThat(context).hasSingleBean(GitPropertiesLoader.class);
              assertThat(context).doesNotHaveBean(ExceptionListener.class);
            });
  }
}

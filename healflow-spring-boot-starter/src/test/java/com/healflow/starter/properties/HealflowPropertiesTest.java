package com.healflow.starter.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.healflow.starter.autoconfigure.HealflowAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HealflowPropertiesTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(HealflowAutoConfiguration.class));

  @Test
  void hasReasonableDefaults() {
    contextRunner.run(
        context -> {
          HealflowProperties properties = context.getBean(HealflowProperties.class);
          assertThat(properties.isEnabled()).isTrue();
          assertThat(properties.isExceptionListenerEnabled()).isTrue();
          assertThat(properties.getHighSeverityToken()).isEqualTo("panic");
        });
  }

  @Test
  void bindsExceptionListenerToggle() {
    contextRunner
        .withPropertyValues("healflow.exception-listener-enabled=false")
        .run(context -> assertThat(context.getBean(HealflowProperties.class).isExceptionListenerEnabled()).isFalse());
  }

  @Test
  void rejectsBlankHighSeverityToken() {
    contextRunner.withPropertyValues("healflow.high-severity-token=").run(context -> assertThat(context).hasFailed());
  }
}


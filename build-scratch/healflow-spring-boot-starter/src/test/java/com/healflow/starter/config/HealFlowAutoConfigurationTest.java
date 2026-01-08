package com.healflow.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.healflow.starter.handler.HealFlowExceptionHandler;
import com.healflow.starter.reporter.IncidentReporter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HealFlowAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(HealFlowAutoConfiguration.class));

  @Test
  void registersReporterAndHandlerByDefault() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(HealFlowProperties.class);
          assertThat(context).hasSingleBean(IncidentReporter.class);
          assertThat(context).hasSingleBean(HealFlowExceptionHandler.class);
          assertThat(context.getBean(HealFlowProperties.class).getGitUrl()).isEmpty();
        });
  }

  @Test
  void bindsGitUrl() {
    contextRunner
        .withPropertyValues("healflow.git-url=https://github.com/Erlin610/healflow.git")
        .run(
            context ->
                assertThat(context.getBean(HealFlowProperties.class).getGitUrl())
                    .isEqualTo("https://github.com/Erlin610/healflow.git"));
  }

  @Test
  void canBeDisabled() {
    contextRunner
        .withPropertyValues("healflow.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(HealFlowProperties.class);
              assertThat(context).doesNotHaveBean(IncidentReporter.class);
              assertThat(context).doesNotHaveBean(HealFlowExceptionHandler.class);
            });
  }
}


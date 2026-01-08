package com.healflow.engine.config;

import static org.junit.jupiter.api.Assertions.*;

import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.engine.shell.ShellRunner;
import com.healflow.engine.shell.InteractiveShellRunner;
import java.lang.reflect.Field;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class SandboxConfigurationConfigTest {

  @Test
  void registersBeansAndAllowsInjection() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(SandboxConfiguration.class);
      context.refresh();

      assertNotNull(context.getBean(ShellRunner.class));
      assertNotNull(context.getBean(DockerSandboxManager.class));
    }
  }

  @Test
  void wiresDockerExecutableFromProperty() throws Exception {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "test", Map.of("healflow.sandbox.docker-executable", "docker-test-bin")));
      context.register(SandboxConfiguration.class);
      context.refresh();

      DockerSandboxManager manager = context.getBean(DockerSandboxManager.class);
      Field dockerExecutable = DockerSandboxManager.class.getDeclaredField("dockerExecutable");
      dockerExecutable.setAccessible(true);
      assertEquals("docker-test-bin", dockerExecutable.get(manager));
    }
  }

  @Test
  void shellRunnerBeanUsesInteractiveImplementation() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.register(SandboxConfiguration.class);
      context.refresh();

      ShellRunner runner = context.getBean(ShellRunner.class);
      assertInstanceOf(InteractiveShellRunner.class, runner);
    }
  }
}

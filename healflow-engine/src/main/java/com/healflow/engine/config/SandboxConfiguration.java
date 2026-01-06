package com.healflow.engine.config;

import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.engine.shell.InteractiveShellRunner;
import com.healflow.engine.shell.ShellRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SandboxConfiguration {

  @Bean
  public ShellRunner shellRunner() {
    return new InteractiveShellRunner();
  }

  @Bean
  public DockerSandboxManager dockerSandboxManager(
      ShellRunner shellRunner,
      @Value("${healflow.sandbox.docker-executable:docker}") String dockerExecutable) {
    return new DockerSandboxManager(shellRunner, dockerExecutable);
  }
}


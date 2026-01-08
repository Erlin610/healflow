package com.healflow.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication(scanBasePackages = "com.healflow")
public class HealflowPlatformApplication {

  public static void main(String[] args) {
    SpringApplication.run(HealflowPlatformApplication.class, args);
  }
}

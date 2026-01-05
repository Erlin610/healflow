package com.healflow.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DemoController {

  @GetMapping("/trigger-error")
  public void triggerError() {
    throw new RuntimeException("Test HealFlow");
  }
}

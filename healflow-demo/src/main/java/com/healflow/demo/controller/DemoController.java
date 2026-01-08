package com.healflow.demo.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.ArrayList;

@RestController
public class DemoController {

  @GetMapping("/trigger-error")
  public void triggerError() {
    throw new RuntimeException("Test HealFlow");
  }

  @GetMapping("/user/{id}")
  public String getUser(@PathVariable String id) {
    // 真实错误：空指针异常
    List<String> users = getUserList();
    return users.get(Integer.parseInt(id));
  }

  private List<String> getUserList() {
    // 故意返回 null 导致 NullPointerException
    return null;
  }
}

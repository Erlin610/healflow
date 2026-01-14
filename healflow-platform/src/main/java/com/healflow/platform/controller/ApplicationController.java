package com.healflow.platform.controller;

import com.healflow.platform.service.ApplicationService;
import com.healflow.platform.service.ApplicationService.ApplicationRequest;
import com.healflow.platform.service.ApplicationService.ApplicationResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

  private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);

  private final ApplicationService applicationService;

  public ApplicationController(ApplicationService applicationService) {
    this.applicationService = applicationService;
  }

  @PostMapping
  public ResponseEntity<ApplicationResponse> create(@RequestBody ApplicationRequest request) {
    log.info("Creating application: {}", request == null ? null : request.appName());
    try {
      return ResponseEntity.ok(applicationService.create(request));
    } catch (IllegalStateException e) {
      return ResponseEntity.status(409).build();
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PutMapping("/{appName}")
  public ResponseEntity<ApplicationResponse> update(
      @PathVariable String appName,
      @RequestBody ApplicationRequest request) {
    log.info("Updating application: {}", appName);
    if (request != null && request.appName() != null && !request.appName().equals(appName)) {
      return ResponseEntity.badRequest().build();
    }
    try {
      return ResponseEntity.ok(applicationService.update(appName, request));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping("/{appName}")
  public ResponseEntity<ApplicationResponse> get(@PathVariable String appName) {
    log.info("Getting application: {}", appName);
    try {
      return ResponseEntity.ok(applicationService.getApplication(appName));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.notFound().build();
    }
  }

  @GetMapping
  public ResponseEntity<List<ApplicationResponse>> list() {
    log.info("Listing applications");
    return ResponseEntity.ok(applicationService.listApplications());
  }

  @DeleteMapping("/{appName}")
  public ResponseEntity<Void> delete(@PathVariable String appName) {
    log.info("Deleting application: {}", appName);
    if (applicationService.delete(appName)) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.notFound().build();
  }
}

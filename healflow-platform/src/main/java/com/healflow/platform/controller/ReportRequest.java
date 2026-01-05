package com.healflow.platform.controller;

import jakarta.validation.constraints.NotBlank;

public record ReportRequest(@NotBlank String incidentReport) {}


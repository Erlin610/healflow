package com.healflow.platform.controller;

import com.healflow.common.dto.IncidentReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    @PostMapping("/report")
    public String receiveReport(@RequestBody IncidentReport report) {
        // 1. 打印接收到的关键信息
        log.info("?? [Alert Received] App: {}, Commit: {}", report.appId(), report.commitId());
        log.error("?? Error: {}: {}", report.errorType(), report.errorMessage());

        // TODO: 下一步将在这里触发 Async Task 去拉取代码

        return "RECEIVED";
    }
}

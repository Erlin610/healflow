package com.healflow.common.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 事故上报 DTO (JDK 21 Record)
 * @param appId 应用ID
 * @param commitId Git版本号
 * @param errorType 异常类型 (e.g. NullPointerException)
 * @param errorMessage 异常消息
 * @param stackTrace 完整堆栈
 * @param environment 环境变量/上下文(e.g. Profile)
 * @param occurredAt 发生时间
 */
public record IncidentReport(
    String appId,
    String commitId,
    String errorType,
    String errorMessage,
    String stackTrace,
    Map<String, String> environment,
    Instant occurredAt
) {}


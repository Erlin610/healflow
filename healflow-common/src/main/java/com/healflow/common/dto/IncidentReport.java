package com.healflow.common.dto;

import java.time.Instant;
import java.util.Map;

/**
 * 事故上报 DTO (JDK 21 Record)
 * @param appId 应用ID
 * @param repoUrl Git 仓库地址（可为空）
 * @param branch Git 分支
 * @param errorType 异常类型 (e.g. NullPointerException)
 * @param errorMessage 异常消息
 * @param stackTrace 完整堆栈
 * @param requestUrl 请求 URL（可为空，非 HTTP 场景）
 * @param requestMethod 请求方法（可为空，非 HTTP 场景）
 * @param requestParams 请求参数/QueryString（可为空，非 HTTP 场景）
 * @param traceId 链路ID（可为空）
 * @param environment 环境变量/上下文(e.g. Profile)
 * @param occurredAt 发生时间
 */
public record IncidentReport(
    String appId,
    String repoUrl, // optional
    String branch,
    String errorType,
    String errorMessage,
    String stackTrace,
    String requestUrl,
    String requestMethod,
    String requestParams,
    String traceId,
    Map<String, String> environment,
    Instant occurredAt
) {}

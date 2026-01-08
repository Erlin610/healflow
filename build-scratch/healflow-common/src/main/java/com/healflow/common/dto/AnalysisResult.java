package com.healflow.common.dto;

public record AnalysisResult(
    String sessionId,
    String structuredOutput,
    String fullText
) {}

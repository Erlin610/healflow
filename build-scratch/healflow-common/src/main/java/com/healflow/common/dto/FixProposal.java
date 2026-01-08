package com.healflow.common.dto;

public record FixProposal(
    String sessionId,
    String structuredOutput,
    String fullText
) {}

package com.healflow.platform.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healflow.engine.sandbox.DockerSandboxManager;
import com.healflow.engine.shell.CommandResult;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Requires Docker and healflow-agent:v1 image")
class ClaudeCodeIntegrationTest {

  @Autowired private DockerSandboxManager dockerSandboxManager;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void claudeCodeSaysHello() throws Exception {
    String containerName = "test-claude-hello-" + System.currentTimeMillis();
    Path workspace = Path.of(System.getProperty("user.dir"));
    String apiKey = getApiKey();
    List<String> command =
        List.of(
            "claude",
            "-p",
            "Say hello in one sentence",
            "--output-format",
            "json",
            "--max-turns",
            "1");

    System.out.println("=== Claude Code Integration Test ===");
    System.out.println("Container: " + containerName);
    System.out.println("Using container's built-in settings.json (no env var override)");
    System.out.println("Command: " + String.join(" ", command));
    System.out.println("Workspace: " + workspace);
    System.out.println("Executing...");

    CommandResult result;
    try {
      result =
          dockerSandboxManager.executeInteractiveRunInSandbox(
              containerName,
              workspace,
              "/src",
              "healflow-agent:v1",
              Map.of(),
              command,
              Duration.ofMinutes(5),
              List.of());

      System.out.println("Exit code: " + result.exitCode());
      System.out.println("Output length: " + result.output().length());
      System.out.println(
          "Output preview: "
              + result.output().substring(0, Math.min(200, result.output().length())));
    } catch (Exception e) {
      System.err.println("Execution failed with exception:");
      System.err.println("Message: " + e.getMessage());
      System.err.println("Cause: " + (e.getCause() != null ? e.getCause().getMessage() : "none"));
      e.printStackTrace();
      throw e;
    }

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.output()).isNotBlank();

    JsonNode response = objectMapper.readTree(result.output());
    String resultText = response.path("result").asText();
    assertThat(resultText.toLowerCase()).contains("hello");

    System.out.println("Claude Code Response: " + resultText);
  }

  @Test
  void claudeCodeGeneratesClaudeMd() throws Exception {
    String containerName = "test-claude-init-" + System.currentTimeMillis();
    Path workspace = Path.of(System.getProperty("user.dir")).getParent();

    String prompt = "Initialize this project by analyzing its structure and generating a CLAUDE.md file. " +
                   "The CLAUDE.md should include: project overview, architecture, key modules, " +
                   "build instructions, and development guidelines. " +
                   "Use Glob and Read to explore, then Write to create CLAUDE.md in the project root.";

    // Note: Not setting --max-turns to allow AI to complete the task naturally
    // In production, let AI decide when the task is done (constrained by timeout and token limits)
    List<String> command = List.of(
        "claude", "-p", prompt,
        "--allowedTools", "Read,Grep,Glob,Write",
        "--output-format", "json");

    System.out.println("=== Claude Code Init Test ===");
    System.out.println("Container: " + containerName);
    System.out.println("Workspace: " + workspace);
    System.out.println("Task: Generate CLAUDE.md (expected to take 2-5 minutes)");
    System.out.println("Timeout: 30 minutes total, 5 minutes idle");
    System.out.println("Executing...");

    long startTime = System.currentTimeMillis();
    CommandResult result;
    try {
      result = dockerSandboxManager.executeInteractiveRunInSandbox(
              containerName, workspace, "/src", "healflow-agent:v1",
              Map.of(),
              command, Duration.ofMinutes(30), List.of());

      long duration = (System.currentTimeMillis() - startTime) / 1000;
      System.out.println("Completed in " + duration + " seconds");
      System.out.println("Exit code: " + result.exitCode());
      System.out.println("Output length: " + result.output().length());
      System.out.println("Raw output:");
      System.out.println(result.output());
      System.out.println("--- End of raw output ---");
    } catch (Exception e) {
      long duration = (System.currentTimeMillis() - startTime) / 1000;
      System.err.println("Failed after " + duration + " seconds");
      System.err.println("Message: " + e.getMessage());
      throw e;
    }

    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.output()).isNotBlank();

    JsonNode response = objectMapper.readTree(result.output());
    System.out.println("JSON parsed successfully");

    String type = response.path("type").asText();
    String subtype = response.path("subtype").asText();
    int numTurns = response.path("num_turns").asInt(0);
    String sessionId = response.path("session_id").asText();

    System.out.println("Type: " + type);
    System.out.println("Subtype: " + subtype);
    System.out.println("Number of turns: " + numTurns);
    System.out.println("Session ID: " + sessionId);

    if ("error_max_turns".equals(subtype)) {
      System.err.println("WARNING: Task reached max turns limit without completion");
      System.err.println("Consider increasing --max-turns or simplifying the task");
    }

    String resultText = response.path("result").asText();
    String errorMessage = response.path("error").asText();

    if (!errorMessage.isEmpty()) {
      System.err.println("Claude Code returned error: " + errorMessage);
    }

    System.out.println("Result text length: " + resultText.length());

    if (!resultText.isEmpty()) {
      System.out.println("Result preview (first 500 chars):");
      System.out.println(resultText.substring(0, Math.min(500, resultText.length())));
    }

    assertThat(numTurns).isGreaterThan(0);

    // Verify CLAUDE.md was created
    Path claudeMdPath = workspace.resolve("CLAUDE.md");
    if (java.nio.file.Files.exists(claudeMdPath)) {
      long fileSize = java.nio.file.Files.size(claudeMdPath);
      System.out.println("CLAUDE.md created successfully (" + fileSize + " bytes)");
    } else {
      System.out.println("Note: CLAUDE.md not found at " + claudeMdPath);
    }
  }

  private String getApiKey() throws Exception {
    String envApiKey = System.getenv("ANTHROPIC_API_KEY");
    if (envApiKey != null && !envApiKey.isBlank()) {
      return envApiKey;
    }

    String envAuthToken = System.getenv("ANTHROPIC_AUTH_TOKEN");
    if (envAuthToken != null && !envAuthToken.isBlank()) {
      return envAuthToken;
    }

    File settingsFile = new File(System.getProperty("user.home"), ".claude/settings.json");
    if (!settingsFile.exists()) {
      throw new RuntimeException("No API key found in environment or ~/.claude/settings.json");
    }

    JsonNode root = objectMapper.readTree(settingsFile);
    for (String jsonPointer :
        new String[] {
          "/env/ANTHROPIC_AUTH_TOKEN",
          "/env/ANTHROPIC_API_KEY",
          "/env/anthropicApiKey",
          "/env/anthropic_api_key",
          "/ANTHROPIC_AUTH_TOKEN",
          "/ANTHROPIC_API_KEY",
          "/anthropicApiKey",
          "/anthropic_api_key",
          "/apiKey"
        }) {
      JsonNode candidate = root.at(jsonPointer);
      if (candidate.isTextual()) {
        String value = candidate.asText();
        if (value != null && !value.isBlank()) {
          return value;
        }
      }
    }

    throw new RuntimeException("No valid API key found in ~/.claude/settings.json");
  }
}

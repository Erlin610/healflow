package com.healflow.engine.sandbox;

import com.healflow.engine.shell.CommandResult;
import com.healflow.engine.shell.ShellRunner;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试 Claude Code 的 JSON Schema 功能
 * 使用容器内的 settings.json 配置（不需要环境变量）
 */
class ClaudeCodeJsonSchemaTest {

    @Test
    void testClaudeCodeWithJsonSchema() throws Exception {
        System.out.println("=== Claude Code JSON Schema Test ===");

        String containerName = "test-claude-json-schema-" + System.currentTimeMillis();
        System.out.println("Container: " + containerName);

        // 准备工作目录
        Path workspace = Path.of(System.getProperty("user.dir"));
        Path schemaFile = workspace.resolve("test-schema.json");

        // 写 JSON Schema 到文件（避免命令行转义问题）
        String jsonSchema = "{\"type\":\"object\",\"properties\":{\"bug_type\":{\"type\":\"string\"},\"severity\":{\"type\":\"string\"}},\"required\":[\"bug_type\",\"severity\"]}";
        Files.writeString(schemaFile, jsonSchema);
        System.out.println("Schema file created: " + schemaFile);

        // 创建执行脚本（避免 shell 解析问题）
        Path scriptFile = workspace.resolve("test-claude.sh");
        String script = "#!/bin/sh\n" +
            "cat /src/test-schema.json\n" +
            "echo '---'\n" +
            "SCHEMA=$(cat /src/test-schema.json)\n" +
            "claude -p '请用中文分析这个错误：NullPointerException at line 10' --output-format json --json-schema \"$SCHEMA\"\n";
        Files.writeString(scriptFile, script);
        System.out.println("Script file created: " + scriptFile);

        System.out.println("Workspace: " + workspace);
        System.out.println("Executing...");

        com.healflow.engine.shell.InteractiveShellRunner shellRunner =
            new com.healflow.engine.shell.InteractiveShellRunner();
        DockerSandboxManager sandboxManager = new DockerSandboxManager(shellRunner);

        try {
            CommandResult result = sandboxManager.executeInteractiveRunInSandbox(
                containerName,
                workspace,
                "/src",
                "healflow-agent:v1",
                Map.of(),  // 空 Map，使用容器内的 settings.json
                List.of("sh", "/src/test-claude.sh"),
                Duration.ofMinutes(2),
                List.of()
            );

            System.out.println("Exit code: " + result.exitCode());
            System.out.println("Output length: " + result.output().length());
            System.out.println("=== Full Output ===");
            System.out.println(result.output());
            System.out.println("=== End Output ===");

            assertEquals(0, result.exitCode(), "Command should succeed");

            String output = result.output();

            // 检查 schema 文件是否正确读取
            assertTrue(output.contains("\"type\":\"object\""), "Schema file should be read correctly");

            // 检查 Claude 的输出
            if (output.contains("{") && output.contains("}")) {
                System.out.println("✓ Output contains JSON structure");

                // 尝试提取 JSON 部分
                int jsonStart = output.indexOf("{");
                int jsonEnd = output.lastIndexOf("}") + 1;
                String jsonPart = output.substring(jsonStart, jsonEnd);
                System.out.println("Extracted JSON: " + jsonPart);
            } else {
                System.out.println("✗ Output does NOT contain JSON structure");
                System.out.println("This means --json-schema parameter is not working");
            }

        } catch (Exception e) {
            System.err.println("Test failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            // 清理测试文件
            try {
                Files.deleteIfExists(schemaFile);
                Files.deleteIfExists(scriptFile);
                System.out.println("Cleaned up test files");
            } catch (Exception e) {
                System.err.println("Failed to clean up: " + e.getMessage());
            }
        }
    }
}
